package net.nennneko5787.lepus.core.format.ir.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.json.JsonValue;

/**
 * {@code textures/flipbook_textures.json}: which textures are animated, and how. SC-180 §8.2.
 *
 * <p><b>Both engines animate a texture the same way</b> — one PNG holding the frames stacked
 * vertically — and both need to be told that is what it is. Bedrock is told here; Java is told by a
 * {@code .mcmeta} beside the file. Without the second one the whole strip is drawn as a single
 * picture squeezed onto one face, which looks like a stretched or smeared texture rather than like a
 * missing animation, and that is what sends someone looking at their UV maths.
 *
 * <p>The two vocabularies line up almost exactly, which is why this is a mapping and not a
 * reimplementation:
 *
 * <table>
 *   <tr><th>Bedrock</th><th>Java</th></tr>
 *   <tr><td>{@code ticks_per_frame}</td><td>{@code frametime}</td></tr>
 *   <tr><td>{@code frames}</td><td>{@code frames}</td></tr>
 *   <tr><td>{@code blend_frames}</td><td>{@code interpolate}</td></tr>
 * </table>
 *
 * <p>Neither engine stores the frame count: both divide the image's height by its width. So nothing
 * here needs to open the PNG.
 */
@SpecImpl("SC-180")
public record FlipbookTextures(Map<String, Flipbook> byTexturePath) {

    public static final FlipbookTextures EMPTY = new FlipbookTextures(Map.of());

    /**
     * One animated texture.
     *
     * @param ticksPerFrame how long a frame shows. Bedrock's default is 1, as Java's is
     * @param frames        the order to play them in, empty meaning "all of them in order"
     * @param blend         whether frames cross-fade. <b>Bedrock defaults this to true and Java
     *                      defaults its counterpart to false</b>, so the default has to be carried
     *                      across explicitly or every animation arrives stepping where it used to
     *                      glide
     */
    public record Flipbook(String texturePath, String atlasTile, int ticksPerFrame,
            List<Integer> frames, boolean blend) {

        public Flipbook {
            frames = List.copyOf(frames);
        }
    }

    public FlipbookTextures {
        byTexturePath = Map.copyOf(byTexturePath);
    }

    /**
     * Reads a parsed {@code flipbook_textures.json}, which is an <b>array</b> at the top level.
     *
     * <p>An entry with no {@code flipbook_texture} is skipped rather than fatal: it names no file,
     * so there is nothing it could animate.
     */
    public static FlipbookTextures of(JsonValue file) {
        Map<String, Flipbook> byPath = new LinkedHashMap<>();
        file.asArray().ifPresent(array -> array.values().forEach(entry -> entry.asObject()
                .ifPresent(object -> {
                    Map<String, JsonValue> members = object.members();
                    Optional<String> path = Optional.ofNullable(members.get("flipbook_texture"))
                            .flatMap(JsonValue::asString);
                    if (path.isEmpty()) {
                        return;
                    }
                    byPath.put(path.get(), new Flipbook(
                            path.get(),
                            Optional.ofNullable(members.get("atlas_tile"))
                                    .flatMap(JsonValue::asString).orElse(""),
                            Optional.ofNullable(members.get("ticks_per_frame"))
                                    .flatMap(JsonValue::asNumber)
                                    .map(number -> number.intValue())
                                    .filter(ticks -> ticks > 0).orElse(1),
                            framesOf(members.get("frames")),
                            Optional.ofNullable(members.get("blend_frames"))
                                    .flatMap(JsonValue::asBool).orElse(true)));
                })));
        return new FlipbookTextures(byPath);
    }

    private static List<Integer> framesOf(JsonValue frames) {
        if (frames == null) {
            return List.of();
        }
        List<Integer> order = new ArrayList<>();
        frames.asArray().ifPresent(array -> array.values().forEach(value ->
                value.asNumber().ifPresent(number -> order.add(number.intValue()))));
        return order;
    }

    /** The animation for a texture path, if it has one. */
    public Optional<Flipbook> forPath(String texturePath) {
        return Optional.ofNullable(byTexturePath.get(texturePath));
    }

    public boolean isEmpty() {
        return byTexturePath.isEmpty();
    }
}
