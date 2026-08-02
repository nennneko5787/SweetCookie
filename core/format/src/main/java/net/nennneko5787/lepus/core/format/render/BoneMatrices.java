package net.nennneko5787.lepus.core.format.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.geometry.BoneIr;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.lepus.core.format.value.Vec3f;

/**
 * Where each bone of a Bedrock model ends up. SC-180 §3, SC-170 §5.
 *
 * <p><b>This is the layer that keeps a rendering bug out of the "launch the client and look" bucket.</b>
 * Everything here — the order the three angles compose in, whether a rotation happens about the
 * pivot or the origin, which way round a parent multiplies a child, what {@code reset} does — is a
 * decision that is invisible in a symmetric model and wrong in an asymmetric one, and all of it is
 * testable in milliseconds because nothing here knows what Minecraft is.
 *
 * <p><b>Bedrock's own space, unconverted.</b> SC-110 §6.1's rule, and the same one the block
 * transpile follows: compute in the space the pack authored in, convert once at the edge. A bone
 * chain computed in a half-converted space is exactly how the block conversion got its face table
 * and its point rotation to disagree about handedness.
 */
@SpecImpl("SC-180")
public final class BoneMatrices {

    /** How deep a parent chain may be before this calls it a cycle. Matches the block transpile. */
    private static final int MAX_DEPTH = 64;

    private BoneMatrices() {
    }

    /**
     * The bind pose: every bone's transform, with no animation applied.
     *
     * <p>Keyed by bone name, in the model's own declaration order so that a caller walking the map
     * draws in the order the pack wrote — which is the order Bedrock draws in, and therefore the
     * order two overlapping translucent faces sort in.
     */
    public static Map<String, Mat4f> bindPose(GeometryIr geometry) {
        return posed(geometry, bone -> Optional.empty());
    }

    /**
     * Every bone's transform, with an extra local transform applied to the bones that have one.
     *
     * <p>The hook animation needs: a bone's animated rotation, position and scale are applied
     * <b>inside</b> its own pivot, before the parent chain carries it away, which is what makes an
     * animated forearm swing about the elbow rather than about the model's origin.
     *
     * @param extra the animation's contribution for a bone, or empty when it is not animated
     */
    public static Map<String, Mat4f> posed(GeometryIr geometry,
            java.util.function.Function<BoneIr, Optional<Mat4f>> extra) {
        Map<String, Mat4f> out = new LinkedHashMap<>();
        for (BoneIr bone : geometry.bones()) {
            chainOf(geometry, bone).ifPresent(chain -> {
                Mat4f matrix = Mat4f.IDENTITY;
                // Outermost first: the parent's transform applies to everything below it, so it is
                // the LEFT factor. Walking this the other way round gives a model whose limbs orbit
                // the world instead of the body, which is unmistakable — but only once something
                // is rotated at all, and a bind pose usually is not.
                for (int i = chain.size() - 1; i >= 0; i--) {
                    matrix = matrix.times(local(chain.get(i), extra.apply(chain.get(i))));
                }
                out.put(bone.name(), matrix);
            });
        }
        return out;
    }

    /**
     * One bone's own transform: its rotation about its pivot, and whatever the animation adds.
     *
     * <p><b>The pivot is not a translation.</b> A bone's rotation happens about its pivot and the
     * bone does not otherwise move, so the transform is {@code T(pivot) · R · T(-pivot)} — and the
     * cubes keep the absolute coordinates the pack gave them. Treating the pivot as a translation
     * moves every cube to the pivot and is the single most common way to get this wrong.
     *
     * <p><b>The rotation order is Z, then Y, then X</b>, matching the order Java's own entity models
     * compose in — the format was Java's before it was Bedrock's. <b>Asserted, not verified.</b> A
     * bone turning about one axis is right whatever the order; only a bone turning about two at once
     * can tell the difference, and this file is where to change it if one does.
     */
    private static Mat4f local(BoneIr bone, Optional<Mat4f> extra) {
        Vec3f pivot = bone.pivot();
        Vec3f rotation = bone.rotation();
        Mat4f matrix = Mat4f.translation(pivot.x(), pivot.y(), pivot.z());
        if (!rotation.isZero()) {
            matrix = matrix.times(rotate(rotation.x(), rotation.y(), rotation.z()));
        }
        if (extra.isPresent()) {
            // Inside the pivot, so an animated joint swings about its own hinge rather than about
            // the model's origin.
            matrix = matrix.times(extra.get());
        }
        return matrix.times(Mat4f.translation(-pivot.x(), -pivot.y(), -pivot.z()));
    }

    /**
     * A Bedrock rotation, in the sense Bedrock means it. SC-180 §3.4.1, SC-150 §5.1 rule 3.
     *
     * <p><b>X and Z turn the opposite way to a right-handed turn. Y does not.</b> The asymmetry is
     * not a quirk to remember — it falls out of the one fact this project already knew, that
     * Bedrock's entity space is the engine's with Y flipped. Conjugating a rotation by that flip
     * reverses the two axes the flipped one takes part in and leaves the third alone:
     * {@code D·Rx(t)·D = Rx(-t)}, {@code D·Rz(t)·D = Rz(-t)}, {@code D·Ry(t)·D = Ry(t)}.
     *
     * <p>The X half was found the hard way: a piggybacking character's legs, posed at −73° to wrap
     * forward around the player, swung backwards. <b>Y was negated at the same time on the
     * assumption that a sign convention must be uniform</b>, and nothing checked it — a bind pose is
     * mostly symmetric, so a reversed Y is invisible until something asymmetric turns about it.
     *
     * <p>Two symptoms, one cause. The same character's legs, at ±24° about Y, crossed instead of
     * spreading — reported as "the legs are too narrow", which is what crossing looks like. And a
     * head driven by {@code query.target_y_rotation} turned the opposite way to the player's while
     * its pitch tracked correctly, since only Y was wrong. Measured, not argued: with Y negated the
     * legs sat at x −3.0..4.1 and −4.3..2.7, overlapping; without, at −7.7..−0.7 and 0.4..7.5,
     * mirrored about the centre line.
     *
     * <p>Composed Z, then Y, then X, matching the order Java's own entity models use — the format
     * was Java's before it was Bedrock's. Asserted; only a bone turning about two axes at once can
     * tell the difference.
     */
    public static Mat4f rotate(float degrees, float aboutY, float aboutZ) {
        return Mat4f.rotationZ(-aboutZ)
                .times(Mat4f.rotationY(aboutY))
                .times(Mat4f.rotationX(-degrees));
    }

    /**
     * A bone and its parents, innermost first.
     *
     * <p>A parent nobody declares ends the chain rather than refusing the model: Bedrock's own files
     * do it, and SC-180 §3.2 already says the hierarchy may be incomplete. Empty when the chain is
     * longer than any real model, which means it is a cycle — following one inside a resource reload
     * would hang the client, and refusing one model is the cheaper answer.
     */
    private static Optional<List<BoneIr>> chainOf(GeometryIr geometry, BoneIr bone) {
        List<BoneIr> chain = new ArrayList<>();
        BoneIr at = bone;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            chain.add(at);
            Optional<String> parent = at.parent();
            if (parent.isEmpty()) {
                return Optional.of(chain);
            }
            Optional<BoneIr> next = geometry.bone(parent.get());
            if (next.isEmpty()) {
                return Optional.of(chain);
            }
            at = next.get();
        }
        return Optional.empty();
    }
}
