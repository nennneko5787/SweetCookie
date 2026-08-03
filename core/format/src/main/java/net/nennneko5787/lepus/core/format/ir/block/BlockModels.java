package net.nennneko5787.lepus.core.format.ir.block;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.json.CanonicalJson;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonArray;
import net.nennneko5787.lepus.core.format.json.JsonBool;
import net.nennneko5787.lepus.core.format.json.JsonNumber;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonString;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockId;

/**
 * Java block models, synthesised from a Bedrock block's materials. SC-150 §5, path A.
 *
 * <p>SC-150 §5 gives two ways to draw a Bedrock block, and says path A — <b>transpile to a Java
 * block model</b> — is the default and the common case. It buys vanilla's chunk meshing, ambient
 * occlusion, culling and lighting for nothing. This is the first step of that path: the model and
 * blockstate JSON a bound slot needs, produced as text so that whatever serves resources can serve
 * it without knowing anything about Bedrock.
 *
 * <p><b>Text, and Minecraft-free.</b> The two loaders supply a resource pack in different ways and
 * the two Minecraft versions do not share a model class, but both consume the same JSON. Generating
 * the JSON here means neither of those differences reaches the generation, and the result is
 * testable by reading it.
 *
 * <p>Only the full-cube case is transpiled so far. A geometry that is not a unit cube needs the
 * bone-and-cube walk SC-150 §5 describes, and falls back to a plain cube rather than to nothing —
 * a block in the wrong shape can be seen and reported; an invisible one reads as "the pack did not
 * load".
 */
@SpecImpl("SC-150")
public final class BlockModels {

    /** Vanilla's own empty model. What an unbound slot draws: nothing at all. */
    public static final String AIR_MODEL = "minecraft:block/air";

    private static final BedrockId MATERIAL_INSTANCES =
            BedrockId.parse("minecraft:material_instances");
    private static final BedrockId GEOMETRY = BedrockId.parse("minecraft:geometry");

    /** The geometry identifier Bedrock uses for a plain cube. */
    private static final String FULL_BLOCK = "minecraft:geometry.full_block";

    /** Every component this class reads. See {@code BlockPhysics.READS} on why it is exported. */
    public static final java.util.Set<BedrockId> READS =
            java.util.Set.of(MATERIAL_INSTANCES, GEOMETRY);

    private BlockModels() {
    }

    /**
     * The texture keys a block's faces use, as {@code material_instances} names them.
     *
     * <p>Keyed by instance name — {@code "*"} for every face, or {@code up} / {@code down} /
     * {@code side} / a face name. The values are Bedrock <b>texture keys</b>, not paths: they index
     * {@code textures/terrain_texture.json} in the resource pack half, and resolving them is a
     * separate step that needs that file.
     */
    public record Materials(Map<String, String> textureKeys, boolean fullCube) {

        public Materials {
            textureKeys = Map.copyOf(textureKeys);
        }

        public static final Materials NONE = new Materials(Map.of(), true);

        /** The texture for a face, falling back to {@code "*"} the way Bedrock does. */
        public Optional<String> textureFor(String face) {
            return Optional.ofNullable(textureKeys.getOrDefault(face, textureKeys.get("*")));
        }

        public boolean isEmpty() {
            return textureKeys.isEmpty();
        }
    }

    /** Reads one resolved state's materials. Anything unrecognised leaves the defaults in place. */
    public static Materials materialsOf(Map<BedrockId, JsonValue> components) {
        Map<String, String> textures = new LinkedHashMap<>();
        JsonValue instances = components.get(MATERIAL_INSTANCES);
        if (instances != null) {
            instances.asObject().ifPresent(object -> object.members().forEach((name, value) ->
                    // An instance is an object with a `texture`; it can also be a STRING naming
                    // another instance to copy. The alias form is resolved after the pass, because
                    // it may name an instance declared later in the same object.
                    value.asObject()
                            .flatMap(entry -> Optional.ofNullable(entry.members().get("texture")))
                            .flatMap(JsonValue::asString)
                            .ifPresent(texture -> textures.put(name, texture))));
            instances.asObject().ifPresent(object -> object.members().forEach((name, value) ->
                    value.asString()
                            .map(textures::get)
                            .ifPresent(texture -> textures.put(name, texture))));
        }
        return new Materials(textures, isFullCube(components.get(GEOMETRY)));
    }

    /**
     * The geometry this block names, when it names one that is not the plain cube.
     *
     * <p>Empty for a block with no {@code minecraft:geometry} and for one naming
     * {@code minecraft:geometry.full_block}: both are the unit cube, which needs no model file
     * looked up and is the commonest block in every pack.
     *
     * <p>Accepts both spellings — the bare string and the object with an {@code identifier} — because
     * both are in circulation and a block written the second way is not a block without geometry.
     */
    public static Optional<String> geometryOf(Map<BedrockId, JsonValue> components) {
        return Optional.ofNullable(components.get(GEOMETRY))
                .flatMap(geometry -> geometry.asString()
                        .or(() -> geometry.asObject()
                                .flatMap(object ->
                                        Optional.ofNullable(object.members().get("identifier")))
                                .flatMap(JsonValue::asString)))
                .filter(identifier -> !FULL_BLOCK.equals(identifier));
    }

    /**
     * True when the geometry is a plain cube, or absent.
     *
     * <p>Absent counts: a Bedrock block with no geometry component is a full cube, and treating that
     * as "unknown shape" would make the commonest block in every pack take the fallback path.
     */
    private static boolean isFullCube(JsonValue geometry) {
        if (geometry == null) {
            return true;
        }
        String identifier = geometry.asString()
                .or(() -> geometry.asObject()
                        .flatMap(object -> Optional.ofNullable(object.members().get("identifier")))
                        .flatMap(JsonValue::asString))
                .orElse(FULL_BLOCK);
        return FULL_BLOCK.equals(identifier);
    }

    /**
     * The blockstate file for one pool slot.
     *
     * <p>One variant per state index, because that is what the index property means. States that
     * share a model share the string; vanilla deduplicates the baked model itself.
     *
     * @param models the model name for each state index, in index order
     */
    public static String blockstateJson(List<String> models) {
        Map<String, JsonValue> variants = new LinkedHashMap<>();
        if (models.size() == 1) {
            // A size-one class carries no property at all, so there is no `i=` to match on and the
            // catch-all key is the only thing that can select anything. Vanilla's air.json is
            // written the same way.
            variants.put("", model(models.get(0)));
        } else {
            for (int index = 0; index < models.size(); index++) {
                variants.put("i=" + index, model(models.get(index)));
            }
        }
        return CanonicalJson.pretty(new JsonObject(Map.of("variants", new JsonObject(variants))));
    }

    /**
     * A cube model for one state.
     *
     * <p>{@code cube_all} when every face is the same texture and {@code cube} when they are not —
     * the same two parents vanilla uses, so the result behaves exactly like a vanilla block model
     * rather than approximately like one.
     *
     * @param textures resolved texture <b>paths</b>, not Bedrock keys, per face name
     */
    public static String cubeModelJson(Map<String, String> textures) {
        Map<String, JsonValue> members = new LinkedHashMap<>();
        boolean uniform = textures.size() == 1 && textures.containsKey("all");
        members.put("parent", new JsonString(
                uniform ? "minecraft:block/cube_all" : "minecraft:block/cube"));
        Map<String, JsonValue> textureMap = new LinkedHashMap<>();
        textures.forEach((face, path) -> textureMap.put(face, new JsonString(path)));
        // particle is what breaking and falling on the block throws off, and vanilla's cube parents
        // do not set it. Absent, it is the missing texture on every interaction.
        textureMap.putIfAbsent("particle",
                new JsonString(textures.values().iterator().next()));
        members.put("textures", new JsonObject(textureMap));
        return CanonicalJson.pretty(new JsonObject(members));
    }

    /**
     * The item model definition naming one model. SC-170 §5.
     *
     * <p>A different file from a model, at {@code items/<name>.json}, and the indirection is what
     * lets one registered item show thousands of shapes: the stack's {@code minecraft:item_model}
     * component names one of these, and this names the model.
     */
    public static String itemModelJson(String model) {
        return CanonicalJson.pretty(new JsonObject(Map.of("model", new JsonObject(Map.of(
                "type", new JsonString("minecraft:model"),
                "model", new JsonString(model))))));
    }

    /**
     * The display contexts the item's own model must draw <b>nothing</b> in. SC-170 §5.
     *
     * <p><b>Every hand, both views</b>, and the reason is that something else draws there. A Bedrock
     * attachable is posed in player space rather than at the hand, so neither view is served by the
     * item's model: third person is a layer on the player, first person is a hook on the hand render
     * that rebuilds player space against the camera. Leaving the sprite here as well puts a flat
     * icon inside the character, which is what a third-person hand did for as long as this list held
     * first person alone.
     *
     * <p>Everything not listed — the inventory, the ground, the item frame — falls through to that
     * sprite, which is what Bedrock shows there.
     */
    private static final List<String> BLANK_CONTEXTS = List.of(
            "firstperson_righthand", "firstperson_lefthand",
            "thirdperson_righthand", "thirdperson_lefthand");

    /**
     * The two of those a <b>vanilla</b> item blanks. SC-170 §5.2.
     *
     * <p>A vanilla item keeps its first-person hands, and the reason is a measurement rather than a
     * preference: a Bedrock client does not draw an attachable whose identifier is a vanilla item in
     * first person at all (0005 {@code probe/}, v3, isolated against a custom item in v8–v10). So
     * something has to draw there and only the item itself is left.
     */
    private static final List<String> BLANK_THIRD_PERSON_CONTEXTS = List.of(
            "thirdperson_righthand", "thirdperson_lefthand");

    /**
     * An item that is a flat icon in the inventory and nothing at all in a hand. SC-170 §5.
     *
     * <p><b>Both halves matter and the first one was learned by breaking it.</b> Making the whole
     * item a special model — which is what a shield or a trident is — took the icon away too: the
     * inventory slot went blank for every item with an attachable, because a special renderer draws
     * in every context and the sprite is never drawn at all. Bedrock does not do that; its
     * attachable is for the hand and its icon is for the slot.
     *
     * <p><b>And the second half was learned by measuring.</b> The hand case used to be that special
     * renderer, which draws where the item is. An attachable is not authored there — a real pack's
     * first-person animation moves the whole character 0.69 blocks in front of the PLAYER — so the
     * model hung below the hand and no translation could fix it. The renderer is gone and the
     * contexts it served now render empty, with the drawing done from the hand render hook.
     *
     * @param sprite the flat model, used everywhere the attachable is not drawn
     */
    public static String heldModelJson(String sprite) {
        return blankingModelJson(sprite, BLANK_CONTEXTS);
    }

    /**
     * A <b>vanilla</b> item that keeps everything but its third-person hands. SC-170 §5.2.
     *
     * <p><b>Vanilla's own definition is wrapped, never rebuilt.</b> Its {@code model} is carried
     * across verbatim as the fallback, so a bow's {@code condition} over {@code using_item} and the
     * {@code range_dispatch} under it, a potion's tint, a compass's needle — all of it survives
     * untouched, and only the two third-person hand contexts become empty. Naming
     * {@code minecraft:item/<path>} instead would have been a guess that happens to be right for
     * plain items and silently breaks every item that is more than one model.
     *
     * <p>Empty when the argument is not an item definition — a file this build cannot read is one it
     * must not replace. The caller then leaves vanilla's own file in place: the cost is a flat sprite
     * drawn inside the character, which is cosmetic, against a broken bow, which is not.
     *
     * @param vanillaDefinition the bytes of vanilla's {@code assets/minecraft/items/<path>.json}
     */
    public static Optional<String> vanillaHeldModelJson(String vanillaDefinition) {
        JsonValue model;
        try {
            model = Json.parse(vanillaDefinition).asObject()
                    .map(object -> object.members().get("model"))
                    .orElse(null);
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
        return model == null
                ? Optional.empty()
                : Optional.of(blankingModelJson(model, BLANK_THIRD_PERSON_CONTEXTS));
    }

    private static String blankingModelJson(String sprite, List<String> contexts) {
        return blankingModelJson(new JsonObject(Map.of(
                "type", new JsonString("minecraft:model"),
                "model", new JsonString(sprite))), contexts);
    }

    private static String blankingModelJson(JsonValue fallback, List<String> contexts) {
        JsonObject blank = new JsonObject(Map.of("type", new JsonString("minecraft:empty")));
        JsonObject held = new JsonObject(Map.of(
                "when", new JsonArray(contexts.stream()
                        .map(context -> (JsonValue) new JsonString(context)).toList()),
                "model", blank));
        return CanonicalJson.pretty(new JsonObject(Map.of("model", new JsonObject(Map.of(
                "type", new JsonString("minecraft:select"),
                "property", new JsonString("minecraft:display_context"),
                "fallback", fallback,
                "cases", new JsonArray(List.of(held)))))));
    }

    /**
     * A flat sprite model, which is what a Bedrock item's icon is.
     *
     * <p>An item has one picture rather than a shape, so this is vanilla's own
     * {@code item/generated} with the picture as layer zero — the same parent every vanilla item
     * uses, so the result behaves exactly like one rather than approximately like one.
     */
    public static String spriteModelJson(String texture) {
        return CanonicalJson.pretty(new JsonObject(Map.of(
                "parent", new JsonString("minecraft:item/generated"),
                "textures", new JsonObject(Map.of("layer0", new JsonString(texture))))));
    }

    /**
     * The {@code .mcmeta} that tells Java a texture is animated. SC-180 §8.2.
     *
     * <p>Emitted beside the PNG, named {@code <texture>.png.mcmeta}. Without it Java draws the whole
     * frame strip as one picture — the symptom is a smeared texture, which reads as a UV bug.
     */
    public static String animationJson(FlipbookTextures.Flipbook flipbook) {
        Map<String, JsonValue> animation = new LinkedHashMap<>();
        animation.put("frametime", JsonNumber.of(flipbook.ticksPerFrame()));
        if (flipbook.blend()) {
            animation.put("interpolate", JsonBool.TRUE);
        }
        if (!flipbook.frames().isEmpty()) {
            animation.put("frames", new JsonArray(flipbook.frames().stream()
                    .map(frame -> (JsonValue) JsonNumber.of(frame)).toList()));
        }
        return CanonicalJson.pretty(new JsonObject(Map.of("animation", new JsonObject(animation))));
    }

    private static JsonValue model(String name) {
        return new JsonObject(Map.of("model", new JsonString(name)));
    }
}
