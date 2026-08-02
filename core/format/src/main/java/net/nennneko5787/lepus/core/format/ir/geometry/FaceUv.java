package net.nennneko5787.lepus.core.format.ir.geometry;

import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.value.Vec2f;

/**
 * One face's texture rectangle. SC-180 §3.
 *
 * <p>In texel units as the pack writes them, never divided by the texture size: the divisor is per
 * model and packs get it wrong, so normalising here would fold a possibly-wrong number into the data
 * and make the mistake unrecoverable.
 *
 * @param uv               top-left corner
 * @param uvSize           extent; negative values are legal and mean the face is flipped
 * @param materialInstance the named material this face uses, when it names one
 */
@SpecImpl("SC-180")
public record FaceUv(Vec2f uv, Vec2f uvSize, Optional<String> materialInstance) {

    public static FaceUv of(Vec2f uv, Vec2f uvSize) {
        return new FaceUv(uv, uvSize, Optional.empty());
    }
}
