package net.nennneko5787.lepus.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.nennneko5787.lepus.client.render.AttachableLayer;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Puts the attachable layer on the player renderers. The 1.21.11 Fabric spelling. SC-170 §5.
 *
 * <p>The 26.2 file is the same registration under the callback's older name — Fabric API renamed it
 * between the two versions, which is why this pair exists rather than one file in
 * {@code src/fabric}. See that file for the axis it opened.
 */
@SpecImpl("SC-170#attachable/geometry")
final class FabricAttachableLayer {

    private FabricAttachableLayer() {
    }

    static void register() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (type, renderer, helper, context) -> {
                    if (renderer instanceof AvatarRenderer player) {
                        helper.register(new AttachableLayer(player));
                    }
                });
    }
}
