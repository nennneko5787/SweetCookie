package net.nennneko5787.lepus.client.render;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.render.AttachablePoser;
import net.nennneko5787.lepus.core.format.render.Mat4f;

/**
 * The bones an attachable names after the player's own, as the player has them this frame.
 * SC-180 §4.2.
 *
 * <p><b>One conversion, two views.</b> Third person reads the posed {@code PlayerModel}; first
 * person has no player model at all and builds the same bones from the player's angles. Keeping
 * both spellings here is the point — the two views disagreeing about where a wearer's bone is was
 * the last first-person defect in this feature, and it could only happen because one view supplied
 * these bones and the other supplied none.
 *
 * <p><b>Head and body only.</b> A Java {@code ModelPart} carries an absolute position rather than a
 * displacement, and only these two rest at the origin; {@code rightArm} sits at {@code (-5, 2, 0)}
 * when the player stands, so feeding its position in as a displacement would throw every arm-bound
 * attachable five units sideways.
 */
@SpecImpl("SC-180#animation/bones")
public final class WearerSkeleton {

    private WearerSkeleton() {
    }

    /** What the player model is doing, for the view that draws one. */
    public static Map<String, Mat4f> of(PlayerModel model) {
        Map<String, Mat4f> skeleton = new LinkedHashMap<>();
        skeleton.put("head", fromPart(model.head));
        skeleton.put("body", fromPart(model.body));
        return skeleton;
    }

    /**
     * The same bones for a view with no player model, <b>as vanilla's own first-person animation
     * writes them</b>. SC-170 §5, SC-180 §4.2.1.
     *
     * <pre>
     * animation.player.first_person.base_pose
     *   body  rotation [ q.target_x_rotation, q.target_y_rotation,       0 ]
     *   head  rotation [ q.target_x_rotation, q.target_y_rotation + 180, 0 ]
     * </pre>
     *
     * <p>That animation is unconditional in the first-person state of
     * {@code controller.animation.player.root}, and it is the ONLY thing that state says about the
     * torso. Nothing writes {@code waist} in that view at all, which is why an attachable hanging
     * off {@code body} behaves differently from one hanging off {@code waist} — the one asymmetry in
     * the corpus, and the only structural difference between the two characters in it.
     *
     * <p><b>Zeroes were passed here for the whole of this feature</b>, so this build has never once
     * supplied the angles vanilla asks for. Five constants were fitted against the resulting frame
     * before anyone noticed that the documented input had never been tried. What those queries
     * answer for a first-person player is §4.3's open question; the angles below are the ones this
     * build can see, and they are at least the right SHAPE.
     *
     * @param pitch       the player's pitch in degrees
     * @param yawFromBody the head's yaw less the body's, in degrees
     */
    public static Map<String, Mat4f> upright(float pitch, float yawFromBody) {
        Map<String, Mat4f> skeleton = new LinkedHashMap<>();
        // READ FROM CORE, so the measuring tool cannot pose these differently. It did, three times.
        skeleton.putAll(AttachablePoser.FIRST_PERSON_WEARER);
        return skeleton;
    }


    private static Mat4f fromPart(ModelPart part) {
        return rotation((float) Math.toDegrees(part.xRot), (float) Math.toDegrees(part.yRot),
                (float) Math.toDegrees(part.zRot), part.x, part.y, part.z);
    }

    /**
     * One of Java's model parts, as a transform in Bedrock's space.
     *
     * <p><b>Java's entity space is Bedrock's with Y flipped</b> (see {@code ON_PLAYER}), and a
     * rotation conjugated by that flip reverses about the two axes the flipped one takes part in and
     * keeps the third: {@code D·Rx(t)·D = Rx(-t)}, {@code D·Rz(t)·D = Rz(-t)}, {@code D·Ry(t)·D =
     * Ry(t)}. That is the same asymmetry SC-180 §3.4.1 records for a pack's own angles, arrived at
     * from the same fact. Composed in the order {@code ModelPart} composes them — Z, then Y, then X.
     *
     * <p>Built with {@link Mat4f}'s own right-handed rotations rather than through
     * {@code BoneMatrices.rotate}: that one applies Bedrock's angle sense, which belongs to angles a
     * PACK wrote and not to a number read out of Java's own model.
     */
    private static Mat4f rotation(float pitch, float yaw, float roll, float x, float y, float z) {
        return Mat4f.translation(x, -y, z)
                .times(Mat4f.rotationZ(-roll))
                .times(Mat4f.rotationY(yaw))
                .times(Mat4f.rotationX(-pitch));
    }
}
