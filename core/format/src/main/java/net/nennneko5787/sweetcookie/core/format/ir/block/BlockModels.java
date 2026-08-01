package net.nennneko5787.sweetcookie.core.format.ir.block;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.json.CanonicalJson;
import net.nennneko5787.sweetcookie.core.format.json.JsonObject;
import net.nennneko5787.sweetcookie.core.format.json.JsonString;
import net.nennneko5787.sweetcookie.core.format.json.JsonValue;
import net.nennneko5787.sweetcookie.core.format.value.BedrockId;

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

    private static JsonValue model(String name) {
        return new JsonObject(Map.of("model", new JsonString(name)));
    }
}
