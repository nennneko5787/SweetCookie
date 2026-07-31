package net.nennneko5787.sweetcookie.core.format.value;

import java.util.List;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * A float pair, in Bedrock's convention. SC-110 §6.1. Mostly texture coordinates.
 *
 * <p>Texture coordinates are kept in <b>texel</b> units, exactly as the pack writes them, and are
 * not divided by the texture size. The divisor is per model ({@code texture_width} and
 * {@code texture_height}) and packs get it wrong; normalising here would fold a possibly-wrong
 * divisor into the data and make the mistake unrecoverable.
 */
@SpecImpl("SC-110")
public record Vec2f(float x, float y) {

    public static final Vec2f ZERO = new Vec2f(0f, 0f);

    /** Reads {@code [x, y]}. Missing components are zero, extras are dropped. */
    public static Vec2f of(List<Float> parts) {
        return new Vec2f(at(parts, 0), at(parts, 1));
    }

    private static float at(List<Float> parts, int index) {
        if (index >= parts.size()) {
            return 0f;
        }
        Float value = parts.get(index);
        return value == null ? 0f : value;
    }

    public Vec2f plus(float dx, float dy) {
        return new Vec2f(x + dx, y + dy);
    }

    public List<Float> toList() {
        return List.of(x, y);
    }
}
