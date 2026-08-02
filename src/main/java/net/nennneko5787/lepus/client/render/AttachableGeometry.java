package net.nennneko5787.lepus.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.geometry.BoneIr;
import net.nennneko5787.lepus.core.format.ir.geometry.CubeFace;
import net.nennneko5787.lepus.core.format.ir.geometry.CubeIr;
import net.nennneko5787.lepus.core.format.ir.geometry.FaceUv;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.lepus.core.format.render.Mat4f;
import net.nennneko5787.lepus.core.format.value.Vec3f;

/**
 * A whole Bedrock model, posed and submitted. SC-170 §5, SC-180 §3.
 *
 * <p>The step {@code BedrockCubeSubmitter} could not take: that spike draws one <b>axis-aligned</b>
 * box, and a posed model has none — every cube arrives rotated by its bone chain, so its eight
 * corners are eight independent points and the box is gone. What survives is the quad, which is why
 * this walks corners rather than extents.
 *
 * <p><b>Version-free on purpose.</b> Everything that differs between 1.21.11 and 26.2 about
 * attachables is in the {@code SpecialModelRenderer} contract around this — the display-context
 * parameter, the generics, where the registry lives — and none of it is here. This file is the part
 * that would otherwise be written twice and drift.
 *
 * <p><b>The two things a compiler cannot check</b>, and where to change them:
 *
 * <ul>
 *   <li>{@link #AS_ITEM} and {@link #ON_PLAYER} — Bedrock's model space against the two spaces it
 *       is drawn in. <b>The caller picks</b>, because they are not the same conversion.
 *   <li>the corner order in {@link #face} — winding, which decides whether a face is visible from
 *       the front or from inside.
 * </ul>
 *
 * <p>Both are settled by looking at a frame, not by reading a jar. They are named and isolated so
 * that settling them is one edit each rather than a hunt.
 */
@SpecImpl(value = "SC-170#attachable/geometry",
        note = "Static pose. Animation, controllers and render controllers are later stages.")
public final class AttachableGeometry {

    /** Bedrock authors at 16 units per block. Every space below shares that much. */
    private static final float SCALE = 1.0f / 16.0f;

    /**
     * Bedrock model units as an <b>item</b> is drawn.
     *
     * <p>Y and Z inverted, which is vanilla's own conversion for an entity model shown as an item —
     * its trident renderer carries {@code scale(1, -1, -1)} for exactly this.
     */
    public static final float[] AS_ITEM = {SCALE, -SCALE, -SCALE};

    /**
     * Bedrock model units as part of a <b>player</b>.
     *
     * <p>Y inverted, X and Z left alone. Java draws an entity model with +Y down, and both engines
     * agree on the other two — observed, after the alternatives were tried on screen.
     *
     * <p><b>X was flipped here for a while, and that was a wrong fix for a real bug.</b> A character
     * posed at −73° had its legs swing backwards, and flipping X made the space's handedness match
     * vanilla's — a plausible-sounding parity argument. It did not help, because the cause was
     * elsewhere: Bedrock's rotation angles turn the opposite way to a right-handed turn, and the
     * bone and animation paths were not applying the correction the block transpile already had.
     * Once that was fixed at the source, the flip remained as a pure mirror, and the model that had
     * been placed on the left shoulder appeared on the right.
     *
     * <p>Worth stating plainly: <b>a change made to fix a misdiagnosed symptom outlives the
     * diagnosis.</b> This one had to be undone by hand after the real cause was found.
     *
     * <p><b>Which space applies belongs to the caller</b>, and this pair is why. One constant inside
     * the emitter meant the item path and the player path shared a decision they do not share.
     */
    public static final float[] ON_PLAYER = {SCALE, -SCALE, SCALE};

    /**
     * Bedrock model units as seen from the <b>first-person camera</b>.
     *
     * <p>In first person there is no player model to hang anything on, so the player space Bedrock
     * poses into has to be rebuilt against the camera. This is that space, and it was <b>solved from
     * {@link #ON_PLAYER} rather than guessed</b> — the third try at a coordinate system in this
     * feature, and the first two were guesses that cost a day each.
     *
     * <p>With the player facing south (yaw 0), so that the world axes can be named:
     *
     * <pre>
     * Bedrock -> world    the layer's chain, R(180) . scale(-1,-1,1) . ON_PLAYER   = diag( 1, 1,-1)/16
     * world   -> camera   camera looks along +Z, so its right is west and its -Z is south
     *                                                                              = diag(-1, 1,-1)
     * Bedrock -> camera                                                            = diag(-1, 1, 1)/16
     * </pre>
     *
     * <p>Reading it back: Bedrock −Z is the direction the player faces and stays the camera's −Z, so
     * the hand animation's {@code position: [0, 0, -11]} puts the model 0.69 blocks in FRONT of the
     * eye, which is where measurement said it lands. Bedrock +X is the model's left and becomes the
     * camera's −X. Bedrock +Y is up in both.
     *
     * <p>Odd parity — one axis flips — and that is correct here rather than the bug it was on
     * {@link #ON_PLAYER}: Bedrock's model space is already mirrored against the world, and the
     * world-to-camera step is a proper rotation, so the composition has to keep the mirror.
     */
    public static final float[] IN_FIRST_PERSON = {-SCALE, SCALE, SCALE};

    private AttachableGeometry() {
    }

    /**
     * Submits every cube of every bone, posed.
     *
     * <p>Cutout rather than solid: Bedrock character models are full of one-pixel-thin quads for
     * hair, eyes and halos, and every one of them has transparent texels. Drawing those as opaque
     * puts a black rectangle where the transparency was, which reads as a broken texture.
     *
     * @param pose the bone matrices from {@code BoneMatrices}, in Bedrock's space
     */
    public static void submit(SubmitNodeCollector collector, PoseStack poseStack,
            Identifier texture, GeometryIr geometry, Map<String, Mat4f> pose, float[] space,
            int light, int overlay, int tint) {
        float width = Math.max(1, geometry.textureWidth());
        float height = Math.max(1, geometry.textureHeight());

        // TWO passes, split by whether a cube has thickness.
        //
        // A Bedrock model decorates a surface with flat quads sitting a hair in front of it — an eye
        // two hundredths of a unit ahead of a face, which at 1/16 scale is a millimetre and a
        // quarter. No depth buffer at Minecraft's view distance separates that, so the two fight and
        // the eye flickers. Bedrock's renderer tolerates the gap; Java's does not.
        //
        // The flat ones therefore go through the Z-offset layer — vanilla's own answer for a decal
        // that would fight the surface it decorates. Nothing is moved, so nothing has to guess which
        // way "outwards" is, which was the alternative and needs a direction this file has not got.
        pass(collector, poseStack, AttachableRenderTypes.solid(texture), geometry, pose, space,
                width, height, light, overlay, tint, false);
        pass(collector, poseStack, AttachableRenderTypes.overlay(texture), geometry, pose, space,
                width, height, light, overlay, tint, true);
    }

    /** One pass over the model, taking either the cubes with thickness or the flat ones. */
    private static void pass(SubmitNodeCollector collector, PoseStack poseStack,
            RenderType renderType, GeometryIr geometry, Map<String, Mat4f> pose, float[] space,
            float width, float height, int light, int overlay, int tint, boolean flatOnes) {
        collector.submitCustomGeometry(poseStack, renderType, (matrix, buffer) -> {
            for (BoneIr bone : geometry.bones()) {
                if (bone.neverRender()) {
                    continue;
                }
                Mat4f bonePose = pose.get(bone.name());
                if (bonePose == null) {
                    // A bone whose chain was refused - a cycle. Skipping it costs that bone rather
                    // than the model, which is the same trade the block transpile makes.
                    continue;
                }
                for (CubeIr cube : bone.cubes()) {
                    boolean flat = flatFace(cube.size().x() + (cube.inflate() + bone.inflate()) * 2,
                            cube.size().y() + (cube.inflate() + bone.inflate()) * 2,
                            cube.size().z() + (cube.inflate() + bone.inflate()) * 2) != null;
                    if (flat != flatOnes) {
                        continue;
                    }
                    submitCube(buffer, matrix, bonePose, bone, cube, width, height, space,
                            light, overlay, tint);
                }
            }
        });
    }

    private static void submitCube(VertexConsumer buffer, PoseStack.Pose matrix, Mat4f bonePose,
            BoneIr bone, CubeIr cube, float width, float height, float[] space,
            int light, int overlay, int tint) {
        float inflate = cube.inflate() + bone.inflate();
        Vec3f origin = cube.origin();
        Vec3f size = cube.size();
        float x0 = origin.x() - inflate;
        float y0 = origin.y() - inflate;
        float z0 = origin.z() - inflate;
        float x1 = origin.x() + size.x() + inflate;
        float y1 = origin.y() + size.y() + inflate;
        float z1 = origin.z() + size.z() + inflate;

        // The eight corners, posed and converted, indexed by the bits of x/y/z. Computed once and
        // shared by the six faces: a corner belongs to three of them, and transforming it three
        // times is both slower and a way for two faces to disagree about where an edge is.
        float[][] corner = new float[8][];
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? x0 : x1;
            float y = (i & 2) == 0 ? y0 : y1;
            float z = (i & 4) == 0 ? z0 : z1;
            float[] posed = bonePose.transform(x, y, z);
            corner[i] = new float[] {
                    posed[0] * space[0], posed[1] * space[1], posed[2] * space[2]};
        }

        // A cube with no thickness on an axis is ONE quad, not two.
        //
        // Bedrock models are full of these: eyes, hair strands, halos and skirt panels are all
        // written as a cube with a zero size on one axis. Emitting both of that axis's faces puts
        // two quads in exactly the same plane, and two coplanar quads fight over the depth buffer —
        // which on screen is the surface tearing and flickering as the camera moves. It looked like
        // a renderer precision problem and it was a counting problem: one surface drawn twice.
        CubeFace flat = flatFace(size.x() + inflate * 2, size.y() + inflate * 2,
                size.z() + inflate * 2);
        for (CubeFace face : CubeFace.values()) {
            if (face == flat) {
                continue;
            }
            cube.face(face).ifPresent(uv -> face(buffer, matrix, corner, face, uv,
                    width, height, light, overlay, tint));
        }
    }

    /**
     * The face to drop when a cube has no thickness, or null when it has some.
     *
     * <p>Which of the pair is dropped decides which way the surviving quad faces, and the answer is
     * "the one pointing at negative" — {@code south} rather than {@code north} — because the model
     * is drawn with culling off, so the survivor is visible from both sides and the choice only
     * decides whose UV is used. Bedrock's own flat cubes give both faces the same rectangle.
     *
     * <p>Zero is compared exactly. A cube one thousandth of a unit thick is a real box a pack
     * meant, and rounding it to flat here would silently drop a face somebody drew.
     */
    private static CubeFace flatFace(float width, float height, float depth) {
        if (depth == 0.0f) {
            return CubeFace.SOUTH;
        }
        if (width == 0.0f) {
            return CubeFace.EAST;
        }
        if (height == 0.0f) {
            return CubeFace.UP;
        }
        return null;
    }

    /**
     * One face, as two triangles' worth of quad.
     *
     * <p>The corner quadruples are wound so that the face is seen from outside the box in <b>Bedrock's</b>
     * space. the conversions mirror an even number of axes and therefore preserve
     * winding — so if faces turn out inside out, the fault is this table and not the conversion.
     *
     * <p>The normal is computed from the corners rather than taken from the face's name, because a
     * posed cube's north face does not point north any more. Lighting reads it, and a stale normal
     * is a model lit from the wrong side — which looks like a texture problem.
     */
    private static void face(VertexConsumer buffer, PoseStack.Pose matrix, float[][] corner,
            CubeFace face, FaceUv uv, float width, float height,
            int light, int overlay, int tint) {
        int[] order = switch (face) {
            // Bits: 1 = x1, 2 = y1, 4 = z1.
            case NORTH -> new int[] {1, 0, 2, 3};
            case SOUTH -> new int[] {4, 5, 7, 6};
            case EAST -> new int[] {5, 1, 3, 7};
            case WEST -> new int[] {0, 4, 6, 2};
            case UP -> new int[] {6, 7, 3, 2};
            case DOWN -> new int[] {0, 1, 5, 4};
        };
        float u0 = uv.uv().x() / width;
        float v0 = uv.uv().y() / height;
        float u1 = (uv.uv().x() + uv.uvSize().x()) / width;
        float v1 = (uv.uv().y() + uv.uvSize().y()) / height;

        float[] a = corner[order[0]];
        float[] b = corner[order[1]];
        float[] c = corner[order[2]];
        float nx = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]);
        float ny = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]);
        float nz = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 1.0e-6f) {
            nx /= length;
            ny /= length;
            nz /= length;
        }

        vertex(buffer, matrix, corner[order[0]], u0, v1, light, overlay, tint, nx, ny, nz);
        vertex(buffer, matrix, corner[order[1]], u1, v1, light, overlay, tint, nx, ny, nz);
        vertex(buffer, matrix, corner[order[2]], u1, v0, light, overlay, tint, nx, ny, nz);
        vertex(buffer, matrix, corner[order[3]], u0, v0, light, overlay, tint, nx, ny, nz);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose matrix, float[] at,
            float u, float v, int light, int overlay, int tint,
            float nx, float ny, float nz) {
        buffer.addVertex(matrix, at[0], at[1], at[2])
                .setColor(tint)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(matrix, nx, ny, nz);
    }
}
