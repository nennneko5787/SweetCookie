package net.nennneko5787.lepus.client.render;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * The two render types a Bedrock model needs. The 26.2 spelling. SC-180 §8.
 *
 * <p><b>Both are no-cull</b>, because a Bedrock model is full of one-sided decoration — a hair
 * strand, an eye, a skirt panel — that is meant to be visible from either side. Here the plain
 * {@code entityCutout} already is; {@code entityCutoutCull} is the culling one.
 *
 * <p>A per-version file because the names differ, not the meaning: 1.21.11 spells the same pair
 * {@code entityCutoutNoCull} and {@code entityCutoutNoCullZOffset}. Reading {@code entityCutout} as
 * "the same thing on both" was itself a bug — it culls on 1.21.11 and does not here, so one version
 * was quietly dropping the back of every flat quad.
 */
@SpecImpl("SC-180")
public final class AttachableRenderTypes {

    private AttachableRenderTypes() {
    }

    /** For cubes with thickness: the ordinary pass. */
    public static RenderType solid(Identifier texture) {
        return RenderTypes.entityCutout(texture);
    }

    /**
     * For flat cubes: the same, pulled towards the camera.
     *
     * <p>{@code VIEW_OFFSET_Z_LAYERING} — vanilla's own answer to a decal that would otherwise fight
     * the surface it decorates. A Bedrock model puts an eye two hundredths of a unit in front of a
     * face; at 1/16 scale that is a millimetre and a quarter, which no depth buffer at Minecraft's
     * view distance can separate. Bedrock's renderer tolerates it and Java's does not, so the
     * decoration is layered instead of nudged — no guess about which way "outwards" is.
     */
    public static RenderType overlay(Identifier texture) {
        return RenderTypes.entityCutoutZOffset(texture);
    }
}
