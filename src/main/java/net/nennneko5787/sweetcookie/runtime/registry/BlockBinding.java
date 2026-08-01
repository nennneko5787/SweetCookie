package net.nennneko5787.sweetcookie.runtime.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.sweetcookie.SweetCookie;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.ir.AddonIr;
import net.nennneko5787.sweetcookie.core.format.ir.BehaviorIr;
import net.nennneko5787.sweetcookie.core.format.ir.PackIr;
import net.nennneko5787.sweetcookie.core.format.ir.block.BlockDefIr;
import net.nennneko5787.sweetcookie.core.format.ir.block.BlockModels;
import net.nennneko5787.sweetcookie.core.format.ir.block.BlockPhysics;
import net.nennneko5787.sweetcookie.core.format.value.BedrockId;
import net.nennneko5787.sweetcookie.core.format.value.PackId;
import net.nennneko5787.sweetcookie.core.registry.BlockLedger;
import net.nennneko5787.sweetcookie.core.registry.BlockSlot;
import net.nennneko5787.sweetcookie.core.registry.IdMapper;
import net.nennneko5787.sweetcookie.core.registry.StateSchema;
import net.nennneko5787.sweetcookie.runtime.addon.WorldActivation;
import net.nennneko5787.sweetcookie.runtime.resource.AddonResourcePack;

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
        Optional<AddonIr> ir = SweetCookie.addons().ir();
        if (ledger.isEmpty() || ir.isEmpty()) {
            return;
        }

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
                .ifPresent(binding -> bound.put(binding.slot(), new BoundBlocks.Bound(
                        binding.logicalId(),
                        definition.resolveAll().stream().map(BlockPhysics::of).toList()))));
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
        Map<String, String> files = new LinkedHashMap<>();
        for (BlockSlot slot : SweetCookie.blockPool().slots()) {
            List<String> models = new ArrayList<>();
            BoundBlocks.at(slot).ifPresent(block -> {
                for (int index = 0; index < block.byStateIndex().size(); index++) {
                    String name = "block/" + slot.sizeClass() + "_" + index;
                    files.put("models/" + name + ".json", BlockModels.cubeModelJson(
                            Map.of("all", "sweetcookie:block/missing")));
                    models.add("sweetcookie:" + name);
                }
            });
            if (models.isEmpty()) {
                models.add(BlockModels.AIR_MODEL);
            }
            files.put("blockstates/" + pathOf(slot) + ".json", BlockModels.blockstateJson(models));
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
                        "[SweetCookie] " + remapped.binding().logicalId()
                                + " changed its states since this world last loaded; blocks already"
                                + " placed were remapped to " + remapped.binding().slot()
                                + " (was " + remapped.previous().size() + " states, now "
                                + remapped.binding().schema().size() + ")");
                case BlockLedger.Outcome.Reallocated moved -> System.out.println(
                        "[SweetCookie] " + moved.binding().logicalId()
                                + " outgrew its slot and moved from " + moved.previousSlot()
                                + " to " + moved.binding().slot() + "; placed blocks were remapped");
                case BlockLedger.Outcome.Exhausted exhausted -> System.out.println(
                        "[SweetCookie] SCE-4010 no free slot for " + exhausted.logicalId()
                                + ". Raise sweetcookie.blockPool." + exhausted.sizeClass()
                                + " by at least " + exhausted.needed()
                                + " in config/sweetcookie.json and restart; that block is not"
                                + " placeable until then and nothing already placed is affected");
            }
        }
        if (allocated > 0 || unchanged > 0) {
            System.out.println("[SweetCookie] bound " + (allocated + unchanged) + " block(s): "
                    + allocated + " newly allocated, " + unchanged + " already bound");
        }
    }
}
