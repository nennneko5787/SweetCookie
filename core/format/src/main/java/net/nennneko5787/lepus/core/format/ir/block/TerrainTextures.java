package net.nennneko5787.lepus.core.format.ir.block;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.json.JsonValue;

/**
 * {@code textures/terrain_texture.json}: block texture keys to the files behind them. SC-180 §.
 *
 * <p>A block's {@code material_instances} names a <b>key</b>, not a path. Nothing renders until the
 * key is looked up here, which is why this sits between {@link BlockModels} reading the materials
 * and anything drawing them.
 *
 * <p>The value is three shapes in real packs — a string, an array of strings for a block with
 * variants, or an object with a {@code path} beside colour data. All three appear in Mojang's own
 * samples, so reading only the string form leaves a working pack looking like a broken one.
 */
@SpecImpl("SC-180")
public record TerrainTextures(Map<String, List<String>> byKey) {

    public static final TerrainTextures EMPTY = new TerrainTextures(Map.of());

    public TerrainTextures {
        byKey = Map.copyOf(byKey);
    }

    /**
     * Reads a parsed {@code terrain_texture.json}.
     *
     * <p>Anything unreadable is skipped rather than fatal — an entry with no usable path costs that
     * one texture, and the block still draws with the rest.
     */
    public static TerrainTextures of(JsonValue file) {
        Map<String, List<String>> byKey = new LinkedHashMap<>();
        file.asObject()
                .flatMap(root -> Optional.ofNullable(root.members().get("texture_data")))
                .flatMap(JsonValue::asObject)
                .ifPresent(data -> data.members().forEach((key, entry) -> {
                    List<String> paths = pathsOf(entry);
                    if (!paths.isEmpty()) {
                        byKey.put(key, paths);
                    }
                }));
        return new TerrainTextures(byKey);
    }

    private static List<String> pathsOf(JsonValue entry) {
        JsonValue textures = entry.asObject()
                .flatMap(object -> Optional.ofNullable(object.members().get("textures")))
                .orElse(null);
        if (textures == null) {
            return List.of();
        }
        Optional<String> single = textures.asString().or(() -> pathOf(textures));
        if (single.isPresent()) {
            return List.of(single.get());
        }
        return textures.asArray()
                .map(array -> array.values().stream()
                        .map(element -> element.asString().or(() -> pathOf(element)))
                        .flatMap(Optional::stream)
                        .toList())
                .orElse(List.of());
    }

    /** The object form: {@code { "path": "textures/blocks/x", "overlay_color": "#ffffff" }}. */
    private static Optional<String> pathOf(JsonValue value) {
        return value.asObject()
                .flatMap(object -> Optional.ofNullable(object.members().get("path")))
                .flatMap(JsonValue::asString);
    }

    /**
     * The file behind a key, without its extension.
     *
     * <p>Falls back to treating the key <b>as</b> a path. Packs do write a direct path in
     * {@code material_instances} where a key was expected, and Bedrock renders those; refusing would
     * make a block that works in Bedrock invisible here, which is the wrong way to be strict.
     *
     * <p>The first entry of a variant list wins. Bedrock picks between variants per position with a
     * hash; picking one is a visible simplification and a visible block, and is the thing to revisit
     * when variants are implemented rather than a reason to draw nothing now.
     */
    public Optional<String> resolve(String key) {
        List<String> paths = byKey.get(key);
        if (paths == null || paths.isEmpty()) {
            return key.contains("/") ? Optional.of(key) : Optional.empty();
        }
        return Optional.of(paths.get(0));
    }

    /** How many keys this pack declares. Worth reporting: zero usually means the file was missed. */
    public int size() {
        return byKey.size();
    }
}
