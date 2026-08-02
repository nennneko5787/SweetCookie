package net.nennneko5787.lepus.runtime.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.Lepus;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.AddonIr;
import net.nennneko5787.lepus.core.format.ir.BehaviorIr;
import net.nennneko5787.lepus.core.format.ir.PackIr;
import net.nennneko5787.lepus.core.format.ir.block.BlockDefIr;
import net.nennneko5787.lepus.core.format.ir.block.BlockModels;
import net.nennneko5787.lepus.core.format.ir.block.BlockPhysics;
import net.nennneko5787.lepus.core.format.ir.block.TerrainTextures;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.registry.BlockLedger;
import net.nennneko5787.lepus.core.registry.BlockSlot;
import net.nennneko5787.lepus.core.registry.IdMapper;
import net.nennneko5787.lepus.core.registry.StateSchema;
import net.nennneko5787.lepus.runtime.addon.WorldActivation;
import net.nennneko5787.lepus.runtime.resource.AddonResourcePack;

/**
 * Gives every enabled pack's blocks a slot in this world. SC-120 §6.
 *
 * <p>The wire between the two halves that already existed. Parsing produced a {@code BlockDefIr} per
 * Bedrock block; registration reserved anonymous slots; the ledger knows how to allocate one and
 * detect drift. Nothing joined them, so a world could enable a pack and the ledger stayed empty.
 *
 * <p><b>Binding is not registration.</b> No Minecraft registry is touched here (constitution rule 7)
 * — this decides which already-registered slot a logical identifier answers to, and writes that
 * decision down. It is why packs attach and detach per world at all.
 */
@SpecImpl({"SC-120", "SC-100"})
public final class BlockBinding {

    /**
     * The merged texture index of the enabled packs, rebuilt on every bind.
     *
     * <p>Merged rather than per-pack because Bedrock resolves a texture key against the whole
     * enabled resource-pack stack: a behaviour pack naming a key its companion resource pack
     * declares is the normal shape of an .mcaddon, not an edge case. Later packs win, which is
     * SC-100 5 again.
     */
    private static TerrainTextures TEXTURES = TerrainTextures.EMPTY;

    private BlockBinding() {
    }

    /**
     * Binds every block of every enabled pack, and persists the result.
     *
     * <p>Called at world load and after any activation change. Idempotent: a block that already has
     * a slot with the same schema is reported {@code Unchanged} and keeps it, so running this on
     * every change costs nothing and cannot move a placed block.
     *
     * <p><b>Disabling never unbinds.</b> {@code bindAll} only adds and updates, and that is
     * deliberate: SC-120 §6.3 rule 1 forbids reusing a slot, so a pack turned off keeps its
     * allocations and turning it back on restores exactly what was placed. Constitution rule 5 — a
     * pack being disabled must never destroy what it built.
     */
    public static void bindEnabled() {
        Optional<BlockLedger> ledger = WorldLedger.current();
        Optional<AddonIr> ir = Lepus.addons().ir();
        if (ledger.isEmpty() || ir.isEmpty()) {
            return;
        }

        TEXTURES = terrainTextures(ir.get());
        Map<BedrockId, BlockDefIr> definitions = enabledBlocks(ir.get());
        if (definitions.isEmpty()) {
            return;
        }

        // Logical identifiers are DERIVED, never allocated (SC-120 §3, ADR-0002). Two machines with
        // the same packs reach the same names with nothing exchanged, which is what lets SC-270 send
        // names instead of negotiated numeric ids.
        Map<BedrockId, String> logical = IdMapper.resolve(new ArrayList<>(definitions.keySet()));

        Map<String, Map.Entry<String, StateSchema>> content = new LinkedHashMap<>();
        definitions.forEach((identifier, definition) -> content.put(
                logical.get(identifier),
                Map.entry(identifier.toString(), StateSchema.of(definition.schema()))));

        report(ledger.get().bindAll(content));
        WorldLedger.save();
        publish(ledger.get(), definitions, logical);
    }

    /**
     * Hands the runtime what each bound slot now behaves like. SC-150 1.
     *
     * <p>Resolved HERE, once per bind, rather than when a collision query asks. A permutation can
     * only see block state, so every state index has a fixed component set and there is nothing left
     * to decide later; doing it later would mean Molang inside getShape.
     */
    private static void publish(BlockLedger ledger, Map<BedrockId, BlockDefIr> definitions,
            Map<BedrockId, String> logical) {
        Map<BlockSlot, BoundBlocks.Bound> bound = new LinkedHashMap<>();
        definitions.forEach((identifier, definition) -> ledger.binding(logical.get(identifier))
                .ifPresent(binding -> {
                    List<Map<BedrockId, JsonValue>> states = definition.resolveAll();
                    bound.put(binding.slot(), new BoundBlocks.Bound(
                            binding.logicalId(),
                            states.stream().map(BlockPhysics::of).toList(),
                            states.stream().map(BlockBinding::textureOf).toList()));
                }));
        BoundBlocks.replace(bound);
        publishResources();
    }

    /**
     * Rebuilds the generated pack for EVERY registered slot, bound or not.
     *
     * <p>Every slot needs a blockstate file or the client reports a missing model for each of its
     * states - 56,832 lines with the default pool, which is not noise around the log but the log.
     * An unbound slot gets one catch-all variant pointing at vanilla empty model: it covers all of
     * that class states in one line, and drawing nothing is what an unclaimed slot should do.
     *
     * <p>Called at mod init as well as after binding, because the client loads resources on its way
     * to the main menu - long before any world, and therefore before anything is bound.
     */
    public static void publishResources() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (BlockSlot slot : Lepus.blockPool().slots()) {
            List<String> models = new ArrayList<>();
            BoundBlocks.at(slot).ifPresent(block -> {
                for (int index = 0; index < block.byStateIndex().size(); index++) {
                    String name = "block/" + slot.sizeClass() + "_" + index;
                    // The texture is emitted beside the model under the same name, so a model and
                    // its picture cannot drift apart. Absent, the model still names it and the block
                    // draws as the missing texture - visible and obviously unfinished.
                    block.textureAt(index).ifPresent(png ->
                            files.put("textures/" + name + ".png", png));
                    files.put("models/" + name + ".json", AddonResourcePack.utf8(
                            BlockModels.cubeModelJson(Map.of("all", "lepus:" + name))));
                    models.add("lepus:" + name);
                }
            });
            if (models.isEmpty()) {
                models.add(BlockModels.AIR_MODEL);
            }
            files.put("blockstates/" + pathOf(slot) + ".json",
                    AddonResourcePack.utf8(BlockModels.blockstateJson(models)));
        }
        AddonResourcePack.replace(files);
    }

    /** The slot path a blockstate file lives at, matching what BlockPool registered. */
    private static String pathOf(BlockSlot slot) {
        String name = slot.toString();
        return name.substring(name.indexOf(58) + 1).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Every enabled pack blocks, in precedence order, later winning.
     *
     * <p>SC-100 §5: the last pack in the order overrides the ones before it. A {@code LinkedHashMap}
     * walked lowest-first does exactly that — a later definition of the same Bedrock identifier
     * replaces the earlier one and keeps its position, so the winner is the later pack's definition
     * under the same derived name, and the block placed in the world does not move.
     */
    private static Map<BedrockId, BlockDefIr> enabledBlocks(AddonIr ir) {
        List<BehaviorIr> inPrecedenceOrder = new ArrayList<>();
        for (PackId pack : WorldActivation.current().order()) {
            ir.byId(pack).map(PackIr::behavior).ifPresent(inPrecedenceOrder::add);
        }
        return merge(inPrecedenceOrder);
    }

    /** Every enabled pack terrain_texture.json, merged with later packs winning. */
    private static TerrainTextures terrainTextures(AddonIr ir) {
        Map<String, java.util.List<String>> merged = new LinkedHashMap<>();
        for (PackId pack : WorldActivation.current().order()) {
            ir.byId(pack).ifPresent(packIr -> packIr.source().vfs()
                    .read("textures/terrain_texture.json")
                    .ifPresent(source -> {
                        try {
                            merged.putAll(TerrainTextures.of(
                                    net.nennneko5787.lepus.core.format.json.Json.parse(
                                            source.readUtf8())).byKey());
                        } catch (java.io.IOException | RuntimeException unreadable) {
                            // One unreadable index costs that pack textures, not the load.
                        }
                    }));
        }
        return new TerrainTextures(merged);
    }

    /**
     * The PNG behind one state materials, if the packs have one.
     *
     * <p>Reads the first texture the state names - material_instances gives a KEY, terrain_texture
     * turns it into a path, and the pack VFS turns that into bytes. Every step can come up empty and
     * an empty answer is a block drawn with the missing texture, which is visible and reportable;
     * refusing the block over an absent picture would not be.
     */
    private static Optional<byte[]> textureOf(Map<BedrockId, JsonValue> components) {
        return BlockModels.materialsOf(components).textureFor("*")
                .or(() -> BlockModels.materialsOf(components).textureFor("up"))
                .flatMap(TEXTURES::resolve)
                .flatMap(BlockBinding::readTexture);
    }

    /**
     * Finds a texture path in any enabled pack.
     *
     * <p>Searched across packs rather than within the declaring one: Bedrock resolves textures
     * against the whole enabled resource-pack stack, and a behaviour pack naming a texture its
     * companion resource pack provides is the normal shape of an .mcaddon, not an edge case.
     */
    private static Optional<byte[]> readTexture(String path) {
        for (String candidate : List.of(path + ".png", path + ".tga", path)) {
            for (PackIr pack : Lepus.addons().ir().map(AddonIr::packs).orElse(List.of())) {
                Optional<byte[]> bytes = pack.source().vfs().read(candidate).map(source -> {
                    try {
                        return source.read();
                    } catch (java.io.IOException unreadable) {
                        return null;
                    }
                });
                if (bytes.isPresent()) {
                    return bytes;
                }
            }
        }
        return Optional.empty();
    }

    /** The merge itself, taking plain data so it can be tested without a world. */
    static Map<BedrockId, BlockDefIr> merge(List<BehaviorIr> inPrecedenceOrder) {
        Map<BedrockId, BlockDefIr> definitions = new LinkedHashMap<>();
        inPrecedenceOrder.forEach(behavior -> definitions.putAll(behavior.blocks()));
        return definitions;
    }

    /**
     * Says what happened, at the volume each outcome deserves.
     *
     * <p>Counts for the ordinary ones — a pack with 200 blocks must not print 200 lines. A line each
     * for the two that change what a player sees: schema drift, which silently remapped placed
     * blocks, and exhaustion, which needs a number and a config key (SC-120 §8.1). Those are the
     * only things here anyone can act on.
     */
    private static void report(List<BlockLedger.Outcome> outcomes) {
        int allocated = 0;
        int unchanged = 0;
        for (BlockLedger.Outcome outcome : outcomes) {
            switch (outcome) {
                case BlockLedger.Outcome.Allocated ignored -> allocated++;
                case BlockLedger.Outcome.Unchanged ignored -> unchanged++;
                case BlockLedger.Outcome.Remapped remapped -> System.out.println(
                        "[Lepus] " + remapped.binding().logicalId()
                                + " changed its states since this world last loaded; blocks already"
                                + " placed were remapped to " + remapped.binding().slot()
                                + " (was " + remapped.previous().size() + " states, now "
                                + remapped.binding().schema().size() + ")");
                case BlockLedger.Outcome.Reallocated moved -> System.out.println(
                        "[Lepus] " + moved.binding().logicalId()
                                + " outgrew its slot and moved from " + moved.previousSlot()
                                + " to " + moved.binding().slot() + "; placed blocks were remapped");
                case BlockLedger.Outcome.Exhausted exhausted -> System.out.println(
                        "[Lepus] SCE-4010 no free slot for " + exhausted.logicalId()
                                + ". Raise lepus.blockPool." + exhausted.sizeClass()
                                + " by at least " + exhausted.needed()
                                + " in config/lepus.json and restart; that block is not"
                                + " placeable until then and nothing already placed is affected");
            }
        }
        if (allocated > 0 || unchanged > 0) {
            System.out.println("[Lepus] bound " + (allocated + unchanged) + " block(s): "
                    + allocated + " newly allocated, " + unchanged + " already bound");
        }
    }
}
