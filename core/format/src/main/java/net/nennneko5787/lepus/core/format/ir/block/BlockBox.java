package net.nennneko5787.lepus.core.format.ir.block;

import java.util.List;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.json.JsonValue;

/**
 * One axis-aligned box read out of {@code collision_box} or {@code selection_box}. SC-150 §4.
 *
 * <p>Held in <b>Java's pixel space</b>: a full cube is {@code 0,0,0 → 16,16,16}. Bedrock states the
 * same box as an {@code origin} measured from the block's centre on X/Z and its floor on Y, plus a
 * {@code size}; the {@code +8} that reconciles them is applied once, here, rather than at every
 * place that wants a box. {@code Block.box} on the Minecraft side takes pixel units, so that side
 * passes these numbers through untouched.
 *
 * <p>Bedrock gives a block a <b>single</b> AABB where Java takes a union of them, so everything
 * Bedrock can express is expressible here. The reverse is not, and does not need to be.
 *
 * @param minX inclusive lower corner, in pixels from the block's own origin
 * @param maxX exclusive upper corner; never below {@link #minX}
 */
@SpecImpl({"SC-150#minecraft:collision_box", "SC-150#minecraft:selection_box"})
public record BlockBox(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {

    /** What a block with no box component has, and what the {@code true} shorthand means. */
    public static final BlockBox FULL = new BlockBox(0, 0, 0, 16, 16, 16);

    /**
     * The largest box Bedrock accepts, in pixels: 1.875 blocks.
     *
     * <p><b>Not observed on a real client</b> — the coverage ledger's note is the only source, and
     * what Bedrock does with a larger one (truncate it, or refuse the block) is unverified. Truncating
     * is the choice here because refusing would be the outcome constitution rule 5 exists to prevent.
     * One constant to change when it is measured.
     */
    public static final float CAP = 30.0f;

    /** How far outside its own block a box may start, in pixels: not at all. */
    private static final float ORIGIN_MIN = -8.0f;
    private static final float ORIGIN_MAX = 8.0f;

    private static final List<Float> DEFAULT_ORIGIN = List.of(-8.0f, 0.0f, -8.0f);
    private static final List<Float> DEFAULT_SIZE = List.of(16.0f, 16.0f, 16.0f);

    public BlockBox {
        // A negative extent is not a smaller box, it is not a box. Collapsing to a point keeps
        // every consumer's min <= max assumption true without any of them checking.
        maxX = Math.max(minX, maxX);
        maxY = Math.max(minY, maxY);
        maxZ = Math.max(minZ, maxZ);
    }

    /**
     * Reads one box component.
     *
     * @param component the component's value, or {@code null} when the block does not declare it
     * @return the box, or empty when the block explicitly has none — {@code "collision_box": false},
     *     which is what a plant or a purely decorative block writes and is a real answer rather than
     *     a failure
     */
    public static Optional<BlockBox> of(JsonValue component) {
        if (component == null) {
            return Optional.of(FULL);
        }
        // The boolean shorthand, both ways round: false removes the box, true is the default cube.
        Optional<Boolean> shorthand = component.asBool();
        if (shorthand.isPresent()) {
            return shorthand.get() ? Optional.of(FULL) : Optional.empty();
        }
        Optional<net.nennneko5787.lepus.core.format.json.JsonObject> object =
                component.asObject();
        if (object.isEmpty()) {
            // A number, a string, an array — nothing Bedrock defines. The block keeps its default
            // box and everything else about it, per constitution rule 5.
            return Optional.of(FULL);
        }
        List<Float> origin = vector(object.get().members().get("origin"), DEFAULT_ORIGIN);
        List<Float> size = vector(object.get().members().get("size"), DEFAULT_SIZE);

        float x = clamp(origin.get(0), ORIGIN_MIN, ORIGIN_MAX);
        float y = clamp(origin.get(1), 0.0f, 16.0f);
        float z = clamp(origin.get(2), ORIGIN_MIN, ORIGIN_MAX);
        float width = clamp(size.get(0), 0.0f, CAP);
        float height = clamp(size.get(1), 0.0f, CAP);
        float depth = clamp(size.get(2), 0.0f, CAP);

        // X is offset; Z is offset AND MIRRORED; Y already agrees. The mirror is the same one
        // BlockGeometry applies, and it has to be the same one: SC-150 §5.3's argument for a single
        // conversion is that a pack author places a collision box and a model in one coordinate
        // space. Converting them differently would make every asymmetric block's box sit on the
        // opposite side from the thing a player can see.
        //
        // Mirroring sends the lower Z to the higher one, so the corners are written in the order
        // the record needs rather than in the order they were computed.
        return Optional.of(new BlockBox(
                x + 8.0f, y, 8.0f - (z + depth),
                x + 8.0f + width, y + height, 8.0f - z));
    }

    /**
     * A three-element vector, or the fallback.
     *
     * <p>All or nothing per field: a vector of the wrong length has no defensible reading, and
     * filling the missing axis with a zero would give a flat box that a pack author never wrote.
     * {@code origin} and {@code size} fall back independently, because declaring one and not the
     * other is a shape Bedrock accepts.
     */
    private static List<Float> vector(JsonValue value, List<Float> fallback) {
        if (value == null) {
            return fallback;
        }
        List<Float> read = value.asArray().map(array -> array.floats()).orElse(List.of());
        return read.size() == 3 ? read : fallback;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** True when this box is the whole block. Worth knowing: it is by far the common case. */
    public boolean isFullCube() {
        return equals(FULL);
    }

    /** True when the box has no volume, which draws no outline and stops nothing. */
    public boolean isEmpty() {
        return minX == maxX || minY == maxY || minZ == maxZ;
    }
}
