package net.nennneko5787.sweetcookie.core.format.ir.geometry;

import java.util.LinkedHashMap;
import java.util.Map;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.value.Vec2f;
import net.nennneko5787.sweetcookie.core.format.value.Vec3f;

/**
 * Expands the {@code 1.8.0} family's box UV into per-face UV. SC-180 §3.
 *
 * <p>The IR holds per-face UV only, and this is why: box UV is expressible as per-face UV and the
 * reverse is not, so normalising in this direction loses nothing while normalising in the other
 * would lose every face a pack set individually.
 *
 * <p>The layout is the unwrapped-box arrangement Bedrock and Blockbench share, with the cube's
 * depth ({@code z}) forming the margins:
 *
 * <pre>
 *        +----+----+
 *        | UP |DOWN|
 *   +----+----+----+-----+
 *   |WEST|NRTH|EAST|SOUTH|
 *   +----+----+----+-----+
 * </pre>
 *
 * <p><b>TODO(SC-180): this arrangement is asserted, not verified.</b> It is the layout every
 * reference describes, and no reference is an artifact. Nothing here can distinguish it from the
 * variant with {@code east} and {@code west} exchanged — which is a real possibility given that
 * Bedrock's Z axis is mirrored relative to Java's — because telling them apart requires looking at a
 * rendered image. Until a T3 conformance case does that, {@code geometry/box_uv} stays {@code
 * partial} and says so. The constants are in this one class so that correcting them is a
 * three-line change rather than an audit.
 */
@SpecImpl("SC-180#geometry/box_uv")
public final class BoxUv {

    private BoxUv() {
    }

    /**
     * @param origin the {@code uv: [u, v]} corner the cube declares
     * @param size   the cube's {@code size: [x, y, z]}, whose components set every face's extent
     */
    public static Map<CubeFace, FaceUv> expand(Vec2f origin, Vec3f size) {
        float u = origin.x();
        float v = origin.y();
        float dx = size.x();
        float dy = size.y();
        float dz = size.z();

        Map<CubeFace, FaceUv> faces = new LinkedHashMap<>();
        faces.put(CubeFace.NORTH, face(u + dz, v + dz, dx, dy));
        faces.put(CubeFace.SOUTH, face(u + dz + dx + dz, v + dz, dx, dy));
        faces.put(CubeFace.EAST, face(u + dz + dx, v + dz, dz, dy));
        faces.put(CubeFace.WEST, face(u, v + dz, dz, dy));
        faces.put(CubeFace.UP, face(u + dz, v, dx, dz));
        faces.put(CubeFace.DOWN, face(u + dz + dx, v, dx, dz));
        return faces;
    }

    private static FaceUv face(float u, float v, float width, float height) {
        return FaceUv.of(new Vec2f(u, v), new Vec2f(width, height));
    }
}
