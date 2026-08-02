package net.nennneko5787.lepus.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Submits one Bedrock cube — an axis-aligned box with independent per-face UVs — into the render
 * pipeline.
 *
 * <p><b>This file is the 26.2 render spike, and it lives in the shared source tree on purpose.</b>
 * If it compiles unchanged on {@code 26.2-fabric}, {@code 26.2-neoforge}, {@code 1.21.11-fabric}
 * and {@code 1.21.11-neoforge}, then the geometry submission path needs no version abstraction at
 * all — which is the opposite of what SC-180 and ADR-0004 assumed, and the reason the spike existed.
 *
 * <p>What the spike found, by reading the two jars rather than the release notes:
 *
 * <ul>
 *   <li>{@code MultiBufferSource} is gone in 26.2, but {@link SubmitNodeCollector} is already
 *       present in <em>1.21.11</em> — the renderer rewrite landed at 1.21.9, so both supported
 *       versions have it.</li>
 *   <li>{@code submitCustomGeometry(PoseStack, RenderType, CustomGeometryRenderer)} has a
 *       byte-identical signature on both, and {@code CustomGeometryRenderer} hands back a
 *       {@link VertexConsumer}. Submission deferred the vertex writing into a callback; it did not
 *       abolish it.</li>
 *   <li>{@code RenderType}, {@code Identifier}, {@code PoseStack} and {@code VertexConsumer} sit in
 *       the same packages on both versions.</li>
 * </ul>
 *
 * <p>That last point matters more than it looks. Bedrock render controllers choose geometry,
 * texture and material <em>per frame</em> through Molang, so an API that only accepted immutable
 * uploaded meshes would have been painful to work with. It accepts a callback instead, which is
 * exactly the shape this project needs.
 *
 * <p>Not yet verified: winding order and UV orientation. Those cannot be settled by a compiler, only
 * by a rendered-image golden (SC-180 section 10), and this class will be wrong in some visible way
 * until that test exists.
 */
@SpecImpl(value = "SC-180", note = "Spike. Geometry submission only; no animation, no render controllers.")
public final class BedrockCubeSubmitter {

    private BedrockCubeSubmitter() {
    }

    /** Face order matches Bedrock's {@code uv} object key order. */
    public static final int NORTH = 0;
    public static final int SOUTH = 1;
    public static final int EAST = 2;
    public static final int WEST = 3;
    public static final int UP = 4;
    public static final int DOWN = 5;

    /**
     * Submits an axis-aligned box.
     *
     * <p>Coordinates are in Bedrock model space — the IR preserves Bedrock's convention unchanged
     * and conversion happens here, once, where a rendered image can check it (SC-110 section 6.1).
     * Bedrock models are authored at 16 units per block, so positions are scaled by 1/16.
     *
     * @param faceUv per face, {@code {u0, v0, u1, v1}} already normalised to 0..1 by the parser
     * @param light  packed lightmap coordinates
     * @param tint   packed ARGB
     */
    public static void submitCube(
            SubmitNodeCollector collector,
            PoseStack poseStack,
            Identifier texture,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float[][] faceUv,
            int light,
            int overlay,
            int tint) {

        RenderType renderType = RenderTypes.entityCutout(texture);

        // The callback is invoked by the pipeline at the right phase, not now. Everything it closes
        // over must therefore still be valid then - which is why the box is passed by value rather
        // than as a reference into a mutable model.
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            final float s = 1.0f / 16.0f;
            float ax = x0 * s, ay = y0 * s, az = z0 * s;
            float bx = x1 * s, by = y1 * s, bz = z1 * s;

            quad(buffer, pose, faceUv[NORTH], light, overlay, tint, 0, 0, -1,
                    bx, ay, az, ax, ay, az, ax, by, az, bx, by, az);
            quad(buffer, pose, faceUv[SOUTH], light, overlay, tint, 0, 0, 1,
                    ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);
            quad(buffer, pose, faceUv[EAST], light, overlay, tint, 1, 0, 0,
                    bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz);
            quad(buffer, pose, faceUv[WEST], light, overlay, tint, -1, 0, 0,
                    ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az);
            quad(buffer, pose, faceUv[UP], light, overlay, tint, 0, 1, 0,
                    ax, by, bz, bx, by, bz, bx, by, az, ax, by, az);
            quad(buffer, pose, faceUv[DOWN], light, overlay, tint, 0, -1, 0,
                    ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz);
        });
    }

    private static void quad(
            VertexConsumer buffer, PoseStack.Pose pose, float[] uv,
            int light, int overlay, int tint,
            float nx, float ny, float nz,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4) {

        vertex(buffer, pose, x1, y1, z1, uv[0], uv[3], light, overlay, tint, nx, ny, nz);
        vertex(buffer, pose, x2, y2, z2, uv[2], uv[3], light, overlay, tint, nx, ny, nz);
        vertex(buffer, pose, x3, y3, z3, uv[2], uv[1], light, overlay, tint, nx, ny, nz);
        vertex(buffer, pose, x4, y4, z4, uv[0], uv[1], light, overlay, tint, nx, ny, nz);
    }

    private static void vertex(
            VertexConsumer buffer, PoseStack.Pose pose,
            float x, float y, float z, float u, float v,
            int light, int overlay, int tint,
            float nx, float ny, float nz) {

        buffer.addVertex(pose, x, y, z)
                .setColor(tint)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
