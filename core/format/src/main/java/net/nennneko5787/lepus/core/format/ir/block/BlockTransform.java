package net.nennneko5787.lepus.core.format.ir.block;

import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import net.nennneko5787.lepus.core.format.value.Vec3f;

/**
 * {@code minecraft:transformation}: how a block's model is turned, moved and sized. SC-150 §4.
 *
 * <p><b>This is how a Bedrock block faces a direction.</b> A block with the
 * {@code placement_direction} trait almost always carries one permutation per direction, each
 * setting a rotation — the state decides which permutation applies and the transformation is what
 * actually turns the model. Reading the state and ignoring the transformation gives a block that
 * stores its facing correctly and draws identically whichever way it is placed, which is exactly
 * what the first real add-on to use it did.
 *
 * @param rotation    degrees about the block's centre
 * @param translation in Bedrock's 1/16 units
 * @param scale       a multiplier per axis, 1 meaning unchanged
 */
@SpecImpl("SC-150#minecraft:transformation")
public record BlockTransform(Vec3f rotation, Vec3f translation, Vec3f scale) {

    /** What a block with no transformation has. */
    public static final BlockTransform NONE =
            new BlockTransform(Vec3f.ZERO, Vec3f.ZERO, Vec3f.ONE);

    private static final BedrockId TRANSFORMATION = BedrockId.parse("minecraft:transformation");

    /**
     * Every component this class reads. See {@code BlockPhysics.READS} on why it is exported.
     *
     * <p>Absent, the survey called {@code minecraft:transformation} unread while a block's four
     * facings depended on it. A survey that keeps its own list drifts towards flattering the build;
     * this one drifted the other way, which is no better — the whole value of the tool is that its
     * answers are true.
     */
    public static final java.util.Set<BedrockId> READS = java.util.Set.of(TRANSFORMATION);

    /** Reads the component out of a resolved state's components, if it declares one. */
    public static Optional<BlockTransform> of(Map<BedrockId, JsonValue> components) {
        JsonValue value = components.get(TRANSFORMATION);
        if (value == null) {
            return Optional.empty();
        }
        return value.asObject().map(object -> new BlockTransform(
                vector(object.members().get("rotation"), Vec3f.ZERO),
                vector(object.members().get("translation"), Vec3f.ZERO),
                vector(object.members().get("scale"), Vec3f.ONE)));
    }

    /**
     * A three-element vector, or the fallback.
     *
     * <p>All or nothing per field, as {@code BlockBox} does: a vector of the wrong length has no
     * defensible reading, and filling a missing axis with a zero would scale a block to nothing.
     */
    private static Vec3f vector(JsonValue value, Vec3f fallback) {
        if (value == null) {
            return fallback;
        }
        java.util.List<Float> parts = value.asArray().map(array -> array.floats())
                .orElse(java.util.List.of());
        return parts.size() == 3 ? Vec3f.of(parts) : fallback;
    }

    /** True when this transformation would change nothing, so nothing has to be applied. */
    public boolean isIdentity() {
        return rotation.isZero() && translation.isZero()
                && scale.x() == 1.0f && scale.y() == 1.0f && scale.z() == 1.0f;
    }
}
