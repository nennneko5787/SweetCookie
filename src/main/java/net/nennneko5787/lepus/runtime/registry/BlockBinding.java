package net.nennneko5787.lepus.runtime.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.nennneko5787.lepus.Lepus;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.AddonIr;
import net.nennneko5787.lepus.core.format.ir.BehaviorIr;
import net.nennneko5787.lepus.core.format.ir.PackIr;
import net.nennneko5787.lepus.core.format.ir.block.BlockDefIr;
import net.nennneko5787.lepus.core.format.ir.block.BlockGeometry;
import net.nennneko5787.lepus.core.format.ir.block.BlockModels;
import net.nennneko5787.lepus.core.format.ir.block.FlipbookTextures;
import net.nennneko5787.lepus.core.format.ir.block.LegacyBlockIndex;
import net.nennneko5787.lepus.core.format.ir.block.MenuOrder;
import net.nennneko5787.lepus.core.format.ir.block.BlockPhysics;
import net.nennneko5787.lepus.core.format.ir.block.BlockTransform;
import net.nennneko5787.lepus.core.format.ir.block.TerrainTextures;
import net.nennneko5787.lepus.core.format.ir.attachable.AttachableIr;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.lepus.core.format.render.AnimationControllerPlayer;
import net.nennneko5787.lepus.core.format.render.AnimationSampler;
import net.nennneko5787.lepus.core.format.render.AttachablePoser;
import net.nennneko5787.lepus.core.format.render.Playable;
import net.nennneko5787.lepus.core.format.text.DisplayNames;
import net.nennneko5787.lepus.core.format.ir.item.ItemDefIr;
import net.nennneko5787.lepus.core.format.ir.item.ItemProfile;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.registry.BlockLedger;
import net.nennneko5787.lepus.core.registry.BlockSlot;
import net.nennneko5787.lepus.core.registry.IdMapper;
import net.nennneko5787.lepus.core.registry.StateSchema;
import net.nennneko5787.lepus.runtime.addon.WorldActivation;
import net.nennneko5787.lepus.runtime.resource.AddonResourcePack;
import net.nennneko5787.lepus.runtime.resource.ClientResources;

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

    /**
     * Every enabled pack's geometry, by identifier, rebuilt on every bind.
     *
     * <p>Merged with later packs winning, for the reason {@link #TEXTURES} is: Bedrock resolves a
     * geometry against the whole enabled stack, and a behaviour pack naming a model its companion
     * resource pack declares is the ordinary shape of an .mcaddon (SC-110 §11 — resource-pack assets,
     * later pack wins wholesale).
     */
    private static Map<String, GeometryIr> GEOMETRIES = Map.of();

    /**
     * {@code textures/item_texture.json}, merged the same way {@link #TEXTURES} is.
     *
     * <p>A separate index from the block one, because Bedrock keeps two and the same key may appear
     * in both meaning different pictures.
     */
    private static TerrainTextures ITEM_TEXTURES = TerrainTextures.EMPTY;

    /**
     * The resource packs' root {@code blocks.json}, merged with later packs winning.
     *
     * <p>Where a block written in the {@code 1.13}-era format keeps its texture. Not a fallback for
     * a malformed pack — it is the only place those blocks say what they look like, and that format
     * is everywhere.
     */
    private static LegacyBlockIndex LEGACY_INDEX = LegacyBlockIndex.EMPTY;

    /** Which textures are animated, merged across enabled packs. SC-180 §8.2. */
    private static FlipbookTextures FLIPBOOKS = FlipbookTextures.EMPTY;

    /** The generated lang files, by pack path. Rebuilt on every bind. */
    private static Map<String, byte[]> LANG_FILES = Map.of();

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

        TEXTURES = textureIndex(ir.get(), "textures/terrain_texture.json");
        ITEM_TEXTURES = textureIndex(ir.get(), "textures/item_texture.json");
        LEGACY_INDEX = legacyTextures(ir.get());
        FLIPBOOKS = flipbooks(ir.get());
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
        GEOMETRIES = geometries(Lepus.addons().ir().orElseThrow());
        Map<BlockSlot, BoundBlocks.Bound> bound = new LinkedHashMap<>();
        definitions.forEach((identifier, definition) -> ledger.binding(logical.get(identifier))
                .ifPresent(binding -> {
                    List<Map<BedrockId, JsonValue>> states = definition.resolveAll();
                    List<BoundBlocks.Appearance> appearances = new ArrayList<>();
                    for (int index = 0; index < states.size(); index++) {
                        appearances.add(appearanceOf(
                                identifier, states.get(index), binding.slot(), index));
                    }
                    bound.put(binding.slot(), BoundBlocks.Bound.of(
                            binding.logicalId(),
                            states.stream().map(BlockPhysics::of).toList(),
                            appearances,
                            LEGACY_INDEX.soundFor(identifier).map(BoundSounds::of)
                                    .orElse(net.minecraft.world.level.block.SoundType.STONE)));
                }));
        // Items carry their own identifiers and therefore their own logical ids, and BOTH sets have
        // to reach the lang files: names were localising for blocks and not for items because this
        // map held only the blocks.
        Map<BedrockId, String> itemLogical =
                IdMapper.resolve(new ArrayList<>(enabledItems().keySet()));
        BoundBlocks.replace(bound, menuOrder(ledger, logical));
        BoundItems.replace(items(itemLogical));

        Map<BedrockId, String> named = new LinkedHashMap<>(logical);
        named.putAll(itemLogical);
        LANG_FILES = languages(named, declaredNameKeys());
        publishResources(true);
    }

    /**
     * Every enabled pack's items, resolved. SC-170.
     *
     * <p>No slot and no ledger entry: an item's identity travels in the stack (SC-120 §4), so this is
     * a list rather than an allocation. Later packs win by identifier, as blocks do.
     *
     * <p>Only items that asked to be in the creative menu are listed. A pack's internal items — the
     * ones it hands out by command or recipe — would otherwise bury the ones a player is meant to
     * find, and Bedrock does not show them either.
     */
    private static List<BoundItems.Bound> items(Map<BedrockId, String> logical) {
        Map<BedrockId, ItemDefIr> definitions = enabledItems();
        List<BoundItems.Bound> bound = new ArrayList<>();
        Map<String, BoundAttachables.Bound> attachables = new LinkedHashMap<>();
        definitions.forEach((identifier, definition) -> {
            if (!definition.inCreative()) {
                return;
            }
            String id = logical.get(identifier);
            String base = "item/" + id.replace(':', '_').replace('.', '_');
            Map<String, byte[]> files = new LinkedHashMap<>();
            Optional<byte[]> icon = ITEM_TEXTURES.resolve(iconKeyOf(identifier, definition))
                    .flatMap(BlockBinding::readTexture);
            icon.ifPresent(png -> files.put("textures/" + base + ".png", png));
            // A flat sprite, which is what a Bedrock item is: its icon is one picture, not a model.
            files.put("items/" + base.replace('/', '_') + ".json", AddonResourcePack.utf8(
                    BlockModels.itemModelJson("lepus:" + base + "_model")));
            // Always our own path, whether or not the file resolved. Absent, the client draws the
            // missing texture - which reads as "missing" and nothing else. Pointing at a real
            // vanilla texture instead was a placeholder that looked like a deliberate choice: an
            // item with no icon appeared as a barrier block, which is a thing, and nobody could tell
            // it was standing in for a failure.
            files.put("models/" + base + "_model.json", AddonResourcePack.utf8(
                    BlockModels.spriteModelJson("lepus:" + base)));
            if (icon.isEmpty()) {
                System.out.println("[Lepus] SCE-2032 " + identifier
                        + " names the icon \"" + iconKeyOf(identifier, definition)
                        + "\", which resolves to no file in any enabled pack;"
                        + " it is drawn with the missing texture");
            }
            // The 3D model this is held as, when a pack ships one (SC-170 §5). Its texture is a
            // PATH rather than a key: an attachable names the file directly where a block names an
            // entry in terrain_texture.json, so there is no index to go through here.
            Optional<AttachableIr> attachable = attachableOf(identifier);
            Optional<GeometryIr> shape = attachable
                    .flatMap(AttachableIr::defaultGeometry)
                    .map(GEOMETRIES::get);
            if (attachable.isPresent() && shape.isPresent()) {
                String name = "attachable/" + base.substring("item/".length());
                Optional<byte[]> skin = attachable.get().defaultTexture()
                        .flatMap(BlockBinding::readTexture);
                skin.ifPresent(png -> files.put("textures/" + name + ".png", png));
                attachables.put(id, BoundAttachables.Bound.of(shape.get(),
                        Identifier.fromNamespaceAndPath(Lepus.MOD_ID,
                                "textures/" + name + ".png"),
                        new AttachablePoser(shape.get(), animationsOf(attachable.get()),
                                attachable.get().preAnimation())));
                // Flat in the inventory, and nothing at all in a hand - which is what Bedrock does,
                // and NOT what a plain special model does. Replacing the whole item took the icon
                // away with it: every slot holding one of these went blank, because a special
                // renderer draws in every context and the sprite is then never drawn at all.
                // The hand draws nothing HERE because the attachable is drawn against the player
                // rather than against the item; see AttachableLayer and FirstPersonAttachables.
                //
                // ARMOUR keeps its sprite in the hand. Bedrock shows a worn attachable only once it
                // is worn, and a helmet being carried is just a helmet - blanking its hand contexts
                // left the player holding nothing at all. Only something that draws as an
                // attachable WHILE HELD may take the blank.
                boolean worn = ItemProfile.of(definition.components()).javaEquipmentSlot()
                        .isPresent();
                if (!worn) {
                    files.put("items/" + base.replace('/', '_') + ".json", AddonResourcePack.utf8(
                            BlockModels.heldModelJson("lepus:" + base + "_model")));
                }
                if (skin.isEmpty()) {
                    System.out.println("[Lepus] SCE-2032 " + identifier
                            + " is held as " + attachable.get().defaultGeometry().orElse("?")
                            + ", whose texture \""
                            + attachable.get().defaultTexture().orElse("<none>")
                            + "\" resolves to no file in any enabled pack");
                }
            }
            bound.add(new BoundItems.Bound(id, base.replace('/', '_'),
                    ItemProfile.of(definition.components()), files));
        });
        BoundAttachables.replace(attachables);
        return bound;
    }

    /**
     * What an attachable plays, resolved. SC-180 §4, §5.
     *
     * <p><b>Blend expressions travel with their animation and are decided per frame</b>, not here:
     * the answer to {@code v.main_hand && c.is_first_person} depends on who is looking, and binding
     * happens once. See {@code AttachablePoser}.
     *
     * <p><b>A name may resolve to an animation or to a CONTROLLER</b>, and a pack writes both in the
     * same list. Controllers are built after the animations because a controller's states name the
     * same short names — Mojang's elytra one asks for {@code sleeping} and {@code swimming}, and the
     * corpus's own asks for six more. A controller naming another controller resolves to nothing:
     * nothing in the corpus does it, and building that needs a cycle check rather than an ordering.
     *
     * <p>A name that resolves to neither is skipped in silence. Two of the three attachables in the
     * corpus point at {@code controller.animation.elytra.default}, which is Mojang's file and not in
     * the add-on — an absence this build may not fill in, and one Bedrock itself resolves.
     */
    private static List<Map.Entry<Playable, Optional<String>>> animationsOf(
            AttachableIr attachable) {
        Map<String, Playable> byShortName = new LinkedHashMap<>();
        attachable.animations().forEach((shortName, identifier) ->
                resourceOf(resource -> resource.animation(identifier))
                        .ifPresent(animation -> byShortName.put(shortName,
                                new AnimationSampler(animation))));
        attachable.animations().forEach((shortName, identifier) -> {
            if (byShortName.containsKey(shortName)) {
                return;
            }
            resourceOf(resource -> resource.controller(identifier))
                    .ifPresent(controller -> byShortName.put(shortName,
                            new AnimationControllerPlayer(controller, byShortName)));
        });
        List<Map.Entry<Playable, Optional<String>>> out = new ArrayList<>();
        for (AttachableIr.Play play : attachable.animate()) {
            Playable playable = byShortName.get(play.name());
            if (playable == null) {
                continue;
            }
            out.add(Map.entry(playable, play.condition()));
        }
        return out;
    }

    /** The first enabled pack, in order, whose resource half answers. */
    private static <T> Optional<T> resourceOf(
            java.util.function.Function<net.nennneko5787.lepus.core.format.ir.ResourceIr,
                    Optional<T>> ask) {
        return Lepus.addons().ir().flatMap(ir -> {
            T found = null;
            for (PackId pack : WorldActivation.current().order()) {
                Optional<T> candidate = ir.byId(pack).map(PackIr::resource).flatMap(ask);
                if (candidate.isPresent()) {
                    found = candidate.get();
                }
            }
            return Optional.ofNullable(found);
        });
    }

    /**
     * The attachable any enabled pack declares for this item, later packs winning.
     *
     * <p>Keyed by the ITEM's identifier in Bedrock's own files, which is what makes this a lookup
     * rather than a search: an attachable's {@code description.identifier} is the item it is for.
     */
    private static Optional<AttachableIr> attachableOf(BedrockId identifier) {
        return Lepus.addons().ir().flatMap(ir -> {
            AttachableIr found = null;
            for (PackId pack : WorldActivation.current().order()) {
                Optional<AttachableIr> candidate = ir.byId(pack)
                        .map(PackIr::resource)
                        .flatMap(resource -> resource.attachable(identifier));
                if (candidate.isPresent()) {
                    found = candidate.get();
                }
            }
            return Optional.ofNullable(found);
        });
    }

    /**
     * The texture key an item's icon names.
     *
     * <p>{@code minecraft:icon} when the item declares one — as a bare string in the older format
     * and as {@code {"texture": …}} in the newer. Absent, Bedrock falls back to the identifier's own
     * path, which is what most packs rely on rather than writing the component.
     */
    private static String iconKeyOf(BedrockId identifier, ItemDefIr definition) {
        return iconIn(definition)
                // Then the RESOURCE pack's item of the same identifier. Bedrock splits an item in
                // two - the behaviour pack says what it does, the resource pack says what it looks
                // like - and minecraft:icon is only ever in the second. Reading only the first gave
                // every item in such a pack no picture at all.
                .or(() -> resourceItem(identifier).flatMap(BlockBinding::iconIn))
                // Bedrock's own last resort, and what most packs rely on rather than writing the
                // component: the identifier's path is the texture key.
                .orElseGet(identifier::path);
    }

    private static Optional<String> iconIn(ItemDefIr definition) {
        JsonValue icon = definition.components().get(BedrockId.parse("minecraft:icon"));
        return icon == null
                ? Optional.empty()
                : icon.asString().or(() -> icon.asObject()
                        .flatMap(object -> Optional.ofNullable(object.members().get("texture")))
                        .flatMap(JsonValue::asString));
    }

    /** The client-side definition of an item, from any enabled pack's resource half. */
    private static Optional<ItemDefIr> resourceItem(BedrockId identifier) {
        return Lepus.addons().ir().flatMap(ir -> {
            ItemDefIr found = null;
            for (PackId pack : WorldActivation.current().order()) {
                Optional<ItemDefIr> candidate = ir.byId(pack)
                        .map(PackIr::resource)
                        .map(resource -> resource.items().get(identifier));
                if (candidate.isPresent()) {
                    found = candidate.get();
                }
            }
            return Optional.ofNullable(found);
        });
    }

    /**
     * The bound slots in creative-menu order. SC-170 §6.
     *
     * <p>{@code MenuOrder} decides — by pack, in activation order, then by Bedrock's own
     * {@code menu_category} — and this maps its answer onto the slots those blocks were bound to. A
     * block whose pack is enabled but which failed to bind simply is not in the list.
     */
    private static List<BlockSlot> menuOrder(BlockLedger ledger, Map<BedrockId, String> logical) {
        List<BehaviorIr> inPrecedenceOrder = new ArrayList<>();
        Lepus.addons().ir().ifPresent(ir -> {
            for (PackId pack : WorldActivation.current().order()) {
                ir.byId(pack).map(PackIr::behavior).ifPresent(inPrecedenceOrder::add);
            }
        });
        List<BlockSlot> slots = new ArrayList<>();
        for (BedrockId identifier : MenuOrder.of(inPrecedenceOrder)) {
            Optional.ofNullable(logical.get(identifier))
                    .flatMap(ledger::binding)
                    .ifPresent(binding -> slots.add(binding.slot()));
        }
        return slots;
    }

    /**
     * The item-model identifier for a bound block, which is also the path it is generated at.
     *
     * <p>State index zero: an item shows one shape, and choosing which needs Bedrock state names
     * rather than the index — the same rule as {@code /lepus place}, in a smaller place.
     */
    public static String itemModelOf(BlockSlot slot) {
        return assetBase(slot, 0);
    }

    /**
     * How one state looks: its model, and the textures that model names. SC-150 §5.
     *
     * <p>Path A is attempted first and every way it can fail lands in the same place — a unit cube
     * with the block's {@code *} texture, which is what every block looked like before this existed
     * (§5.2). The two failures are reported apart, because their fixes belong to different people:
     * a geometry no pack declares is the author's misspelling, and a geometry this build cannot
     * transpile is ours.
     */
    private static BoundBlocks.Appearance appearanceOf(BedrockId block,
            Map<BedrockId, JsonValue> components, BlockSlot slot, int index) {
        // material_instances first, then the resource pack's blocks.json. A block from the 1.13-era
        // format has no materials at all and keeps its texture there; reading only the modern
        // component leaves it with the missing texture and nothing in its own file to explain why.
        BlockModels.Materials declared = BlockModels.materialsOf(components);
        BlockModels.Materials materials = declared.isEmpty()
                ? LEGACY_INDEX.materialsFor(block).orElse(declared)
                : declared;
        Optional<String> wanted = BlockModels.geometryOf(components);
        Optional<GeometryIr> geometry = wanted.map(GEOMETRIES::get);

        String base = assetBase(slot, index);
        Map<String, String> refs = new LinkedHashMap<>();
        Map<String, byte[]> files = new LinkedHashMap<>();
        // Only the instances the model's faces actually name are resolved. A pack declaring six
        // materials and using two should not ship four textures per state into the client.
        for (String instance : geometry.map(BlockGeometry::instancesUsed).orElse(Set.of("*"))) {
            Optional<String> path = materials.textureFor(instance).flatMap(TEXTURES::resolve);
            Optional<byte[]> png = path.flatMap(BlockBinding::readTexture);
            if (png.isPresent()) {
                String name = "block/" + base + "_" + refs.size();
                refs.put(instance, "lepus:" + name);
                files.put("textures/" + name + ".png", png.get());
                // An animated texture is a strip of frames, and Java has to be told so or it draws
                // the whole strip as one picture - which looks like a stretched texture, not like a
                // missing animation, and sends the reader to the UV maths.
                path.flatMap(FLIPBOOKS::forPath).ifPresent(flipbook -> files.put(
                        "textures/" + name + ".png.mcmeta",
                        AddonResourcePack.utf8(BlockModels.animationJson(flipbook))));
            } else if (index == 0) {
                // Once per block rather than once per state: every state of a block usually names
                // the same materials, and a 32-state block would otherwise report the same missing
                // file 32 times.
                //
                // Reported at all because the symptom without it is a black-and-magenta cube and no
                // line anywhere saying which file is absent. That is the block drawing exactly what
                // SC-150 §5.2 asks it to - visible, not a crash - but visible is only half of it.
                System.out.println("[Lepus] SCE-2032 " + block + " names the texture "
                        + materials.textureFor(instance).map(key -> "\"" + key + "\"")
                                .orElse("<none>")
                        + " for its \"" + instance + "\" material, which resolves to no file in any"
                        + " enabled pack; it is drawn with the missing texture");
            }
        }

        Optional<String> transpiled = geometry.flatMap(model -> BlockGeometry.modelJson(
                model, refs, BlockTransform.of(components).orElse(BlockTransform.NONE)));
        if (wanted.isPresent() && transpiled.isEmpty()) {
            System.out.println("[Lepus] " + (geometry.isEmpty()
                    ? "SCE-2030 " + block + " names the geometry " + wanted.get()
                            + ", which no enabled pack declares"
                    : "SCE-2031 " + block + " uses the geometry " + wanted.get()
                            + ", which this build cannot draw as a Java block model")
                    + "; it is drawn as a cube");
        }
        // The fallback names a texture whether or not one resolved. Absent, the block draws with
        // the missing texture - visible and reportable, where an absent model is an invisible block.
        String fallbackTexture = refs.getOrDefault("*", "lepus:block/" + base + "_0");
        return new BoundBlocks.Appearance(
                transpiled.orElseGet(() -> BlockModels.cubeModelJson(Map.of("all", fallbackTexture))),
                files);
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
     *
     * @param reloadOnChange whether to ask the client to read the pack again if the bytes changed.
     *                       True after binding, when the client's baked models are the ones from
     *                       before this pack existed; false at mod init, where there is nothing
     *                       loaded yet to reload
     */
    private static void publishResources(boolean reloadOnChange) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (BlockSlot slot : Lepus.blockPool().slots()) {
            List<String> models = new ArrayList<>();
            BoundBlocks.at(slot).ifPresent(block -> {
                for (int index = 0; index < block.byStateIndex().size(); index++) {
                    String name = "block/" + assetBase(slot, index);
                    // Model and textures were resolved together at bind time and are written out
                    // together here, so a model and the pictures it names cannot drift apart.
                    block.appearanceAt(index).ifPresent(appearance -> {
                        files.putAll(appearance.textureFiles());
                        files.put("models/" + name + ".json",
                                AddonResourcePack.utf8(appearance.modelJson()));
                    });
                    models.add("lepus:" + name);
                }
                // The item form draws the block's own model. An item model DEFINITION, which is a
                // different file from a model: since 1.21.4 an item names one of these and it names
                // the model, so that one item can show thousands of shapes through a data component.
                files.put("items/" + itemModelOf(slot) + ".json", AddonResourcePack.utf8(
                        BlockModels.itemModelJson(
                                "lepus:block/" + assetBase(slot, 0))));
            });
            if (models.isEmpty()) {
                models.add(BlockModels.AIR_MODEL);
            }
            files.put("blockstates/" + pathOf(slot) + ".json",
                    AddonResourcePack.utf8(BlockModels.blockstateJson(models)));
        }
        // Items bring their own files, already resolved: they have no slot to iterate.
        BoundItems.all().forEach(item -> files.putAll(item.files()));
        files.putAll(LANG_FILES);
        if (AddonResourcePack.replace(files) && reloadOnChange) {
            // The client baked its models before any pack was bound, so without this a bound block
            // is INVISIBLE - correct outline, correct collision, nothing drawn. See ClientResources.
            ClientResources.reload();
        }
    }

    /**
     * Publishes without asking the client to reload.
     *
     * <p>For mod initialisation, which runs before the client's first resource load: there is
     * nothing to reload yet, and asking would either be wasted or reentrant.
     */
    public static void publishResources() {
        publishResources(false);
    }

    /**
     * The name every generated asset for one state is built from.
     *
     * <p><b>Per slot, not per size class.</b> Naming these after the size class alone was a bug with
     * exactly one symptom: two blocks in the same class wrote the same model file, the second won,
     * and one add-on's block silently wore another's model and texture. It needed two bound blocks
     * of one class to show up, which is the second block anyone installs.
     */
    private static String assetBase(BlockSlot slot, int index) {
        return pathOf(slot).replace('/', '_') + "_" + index;
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

    /**
     * The generated lang files: every enabled pack's {@code texts/*.lang}, re-keyed. SC-100 §9.
     *
     * <p>One Java lang file per language any enabled pack ships, holding only the names of content
     * this world actually bound. Re-keyed from Bedrock's {@code tile.<id>.name} onto the logical
     * identifier, because two packs may define the same Bedrock identifier and SC-120 §3 has already
     * decided which is which — keeping Bedrock's key would put them both under one name again.
     *
     * <p>Blocks and items are looked up under their own prefixes and an item may also carry a block
     * key: Bedrock gives a block's item form the {@code tile.} name, and packs rely on it.
     */
    /**
     * The lang key each item names for itself, where it names one. SC-170 §2.
     *
     * <p>{@code minecraft:display_name} does not hold a name — it holds a <b>key</b>, and packs use
     * it to borrow another entry's. In the corpus this was written against, 18 items point at
     * {@code item.spawn_egg.entity.<entity>.name}: a ticket that places an entity is spelled as a
     * spawn egg, and Bedrock keys spawn eggs by the entity rather than by the item. Looking only
     * under {@code item.<identifier>.name} left every one of them showing its own identifier.
     */
    private static Map<BedrockId, String> declaredNameKeys() {
        Map<BedrockId, String> keys = new LinkedHashMap<>();
        enabledItems().forEach((identifier, definition) ->
                ItemProfile.of(definition.components()).nameKey()
                        .ifPresent(key -> keys.put(identifier, key)));
        return keys;
    }

    private static Map<String, byte[]> languages(Map<BedrockId, String> logical,
            Map<BedrockId, String> declaredKeys) {
        Map<String, Map<String, String>> byLocale = new LinkedHashMap<>();
        Lepus.addons().ir().ifPresent(ir -> {
            for (PackId pack : WorldActivation.current().order()) {
                ir.byId(pack).ifPresent(packIr -> packIr.source().texts().byLocale()
                        .forEach((locale, entries) -> {
                            Map<String, String> target = byLocale.computeIfAbsent(
                                    DisplayNames.javaLocale(locale), key -> new LinkedHashMap<>());
                            logical.forEach((identifier, id) -> {
                                // What the pack said its name is keyed under wins over both
                                // defaults, because it is the only one the pack chose deliberately.
                                String declared = declaredKeys.get(identifier);
                                String name = declared == null ? null : entries.get(declared);
                                if (name == null) {
                                    name = entries.get(DisplayNames.blockKey(identifier));
                                }
                                if (name == null) {
                                    name = entries.get(DisplayNames.itemKey(identifier));
                                }
                                if (name != null && !name.isBlank()) {
                                    target.put(DisplayNames.javaKey(id), name);
                                }
                            });
                        }));
            }
        });

        Map<String, byte[]> files = new LinkedHashMap<>();
        byLocale.forEach((locale, entries) -> {
            if (!entries.isEmpty()) {
                files.put("lang/" + locale + ".json",
                        AddonResourcePack.utf8(DisplayNames.langJson(entries)));
            }
        });
        return files;
    }

    /** Every enabled pack's flipbook_textures.json, merged with later packs winning. */
    private static FlipbookTextures flipbooks(AddonIr ir) {
        Map<String, FlipbookTextures.Flipbook> merged = new LinkedHashMap<>();
        for (PackId pack : WorldActivation.current().order()) {
            ir.byId(pack).ifPresent(packIr -> packIr.source().vfs()
                    .read("textures/flipbook_textures.json")
                    .ifPresent(source -> {
                        try {
                            merged.putAll(FlipbookTextures.of(
                                    net.nennneko5787.lepus.core.format.json.Json
                                            .parse(source.readUtf8())).byTexturePath());
                        } catch (java.io.IOException | RuntimeException unreadable) {
                            // One unreadable index costs that pack its animations, not the load.
                        }
                    }));
        }
        return new FlipbookTextures(merged);
    }

    /** Every enabled pack's root blocks.json, merged with later packs winning. */
    private static LegacyBlockIndex legacyTextures(AddonIr ir) {
        Map<String, Map<String, String>> textures = new LinkedHashMap<>();
        Map<String, String> sounds = new LinkedHashMap<>();
        for (PackId pack : WorldActivation.current().order()) {
            ir.byId(pack).ifPresent(packIr -> packIr.source().vfs().read("blocks.json")
                    .ifPresent(source -> {
                        try {
                            LegacyBlockIndex index = LegacyBlockIndex.of(
                                    net.nennneko5787.lepus.core.format.json.Json
                                            .parse(source.readUtf8()));
                            textures.putAll(index.texturesByBlock());
                            sounds.putAll(index.soundByBlock());
                        } catch (java.io.IOException | RuntimeException unreadable) {
                            // One unreadable index costs that pack's legacy textures, not the load.
                        }
                    }));
        }
        return new LegacyBlockIndex(textures, sounds);
    }

    /** Every enabled pack's item definitions, later packs winning by identifier. */
    private static Map<BedrockId, ItemDefIr> enabledItems() {
        Map<BedrockId, ItemDefIr> definitions = new LinkedHashMap<>();
        Lepus.addons().ir().ifPresent(ir -> {
            for (PackId pack : WorldActivation.current().order()) {
                ir.byId(pack).map(PackIr::behavior)
                        .ifPresent(behavior -> definitions.putAll(behavior.items()));
            }
        });
        return definitions;
    }

    /** Every enabled pack's geometry, merged with later packs winning. */
    private static Map<String, GeometryIr> geometries(AddonIr ir) {
        Map<String, GeometryIr> merged = new LinkedHashMap<>();
        for (PackId pack : WorldActivation.current().order()) {
            ir.byId(pack).ifPresent(packIr -> merged.putAll(packIr.resource().geometries()));
        }
        return merged;
    }

    /** One of Bedrock's two texture indexes, merged across enabled packs with later ones winning. */
    private static TerrainTextures textureIndex(AddonIr ir, String path) {
        Map<String, java.util.List<String>> merged = new LinkedHashMap<>();
        for (PackId pack : WorldActivation.current().order()) {
            ir.byId(pack).ifPresent(packIr -> packIr.source().vfs()
                    .read(path)
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
