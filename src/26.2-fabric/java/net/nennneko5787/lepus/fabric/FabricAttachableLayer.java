package net.nennneko5787.lepus.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.nennneko5787.lepus.client.render.AttachableLayer;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Puts the attachable layer on the player renderers. The 26.2 Fabric spelling. SC-170 §5.
 *
 * <p><b>Why this file exists at all.</b> Fabric API renamed the callback between the two supported
 * Minecraft versions — {@code LivingEntityFeatureRendererRegistrationCallback} became
 * {@code LivingEntityRenderLayerRegistrationCallback} at 26.2 — so the divergence is neither purely
 * a version one nor purely a loader one. It is the intersection, and it is what opened the
 * {@code src/<version>-<loader>} axis in the buildscripts.
 *
 * <p>Every living renderer is offered the layer and only the player's takes it. Both player skins
 * come through this one callback, so nothing has to enumerate them.
 */
@SpecImpl("SC-170#attachable/geometry")
final class FabricAttachableLayer {

    private FabricAttachableLayer() {
    }

    static void register() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register(
                (type, renderer, helper, context) -> {
                    if (renderer instanceof AvatarRenderer player) {
                        helper.register(new AttachableLayer(player));
                    }
                });
    }
}
