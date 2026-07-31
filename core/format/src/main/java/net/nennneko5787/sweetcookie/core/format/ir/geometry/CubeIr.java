package net.nennneko5787.sweetcookie.core.format.ir.geometry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.ir.UnknownData;
import net.nennneko5787.sweetcookie.core.format.value.Vec3f;

/**
 * One box in a bone. SC-180 §3.
 *
 * @param origin   the corner with the lowest coordinate on each axis, in Bedrock's convention
 * @param size     extent on each axis
 * @param pivot    the cube's own rotation pivot; {@link Vec3f#ZERO} when it declares none
 * @param rotation degrees about {@code pivot}
 * @param inflate  grows the box by this on every side, without changing its UV
 * @param mirror   the {@code 1.8.0} family's per-cube U flip
 * @param uv       per-face, always — box UV is expanded at parse time (see {@link BoxUv})
 * @param unknown  keys this build does not recognise, kept verbatim (SC-110 §5)
 */
@SpecImpl("SC-180#geometry/cubes")
public record CubeIr(
        Vec3f origin,
        Vec3f size,
        Vec3f pivot,
        Vec3f rotation,
        float inflate,
        boolean mirror,
        Map<CubeFace, FaceUv> uv,
        UnknownData unknown) {

    public CubeIr {
        Map<CubeFace, FaceUv> copy = new LinkedHashMap<>();
        // Iterating the enum rather than the input fixes the order regardless of what the file did,
        // which is what keeps a golden stable (SC-110 §10).
        for (CubeFace face : CubeFace.values()) {
            FaceUv value = uv.get(face);
            if (value != null) {
                copy.put(face, value);
            }
        }
        uv = Collections.unmodifiableMap(copy);
    }

    public Optional<FaceUv> face(CubeFace face) {
        return Optional.ofNullable(uv.get(face));
    }

    /** True when the cube declares a rotation that a renderer has to apply per cube. */
    public boolean isRotated() {
        return !rotation.isZero();
    }
}
