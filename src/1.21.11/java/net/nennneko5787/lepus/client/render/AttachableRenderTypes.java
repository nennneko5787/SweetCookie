package net.nennneko5787.lepus.client.render;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * The two render types a Bedrock model needs. The 1.21.11 spelling. SC-180 §8.
 *
 * <p>The same pair as the 26.2 file under different names: here the no-cull variants say so, and
 * plain {@code entityCutout} culls. See that file for why both are no-cull and what the Z offset is
 * for.
 */
@SpecImpl("SC-180")
public final class AttachableRenderTypes {

    private AttachableRenderTypes() {
    }

    /** For cubes with thickness: the ordinary pass. */
    public static RenderType solid(Identifier texture) {
        return RenderTypes.entityCutoutNoCull(texture);
    }

    /** For flat cubes: the same, pulled towards the camera. */
    public static RenderType overlay(Identifier texture) {
        return RenderTypes.entityCutoutNoCullZOffset(texture);
    }
}
