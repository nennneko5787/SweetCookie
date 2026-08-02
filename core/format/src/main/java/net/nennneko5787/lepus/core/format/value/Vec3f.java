package net.nennneko5787.lepus.core.format.value;

import java.util.List;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * A float triple, in <b>Bedrock's</b> axis convention. SC-110 §6.1.
 *
 * <p><b>No conversion happens here.</b> Bedrock and Java disagree about handedness and about pivot
 * conventions, and the IR preserves Bedrock's unchanged. Converting in the parser was considered and
 * rejected twice over: it would make the IR untestable against Mojang's own sample data, and it
 * would bake a rendering decision into a module that must not know rendering exists.
 *
 * <p>Conversion happens once, in the renderer (SC-180), where it can be checked against an image.
 */
@SpecImpl("SC-110")
public record Vec3f(float x, float y, float z) {

    public static final Vec3f ZERO = new Vec3f(0f, 0f, 0f);
    public static final Vec3f ONE = new Vec3f(1f, 1f, 1f);

    /**
     * Reads {@code [x, y, z]}.
     *
     * <p>Lenient in one direction only: missing components are zero, extras are dropped. A vector of
     * the wrong length is a malformed value the caller reports — this method has no provenance to
     * report it against, and returning something usable is what keeps a broken cube from taking the
     * whole model with it.
     */
    public static Vec3f of(List<Float> parts) {
        return new Vec3f(at(parts, 0), at(parts, 1), at(parts, 2));
    }

    private static float at(List<Float> parts, int index) {
        if (index >= parts.size()) {
            return 0f;
        }
        Float value = parts.get(index);
        return value == null ? 0f : value;
    }

    public Vec3f plus(Vec3f other) {
        return new Vec3f(x + other.x, y + other.y, z + other.z);
    }

    public boolean isZero() {
        return x == 0f && y == 0f && z == 0f;
    }

    public List<Float> toList() {
        return List.of(x, y, z);
    }
}
