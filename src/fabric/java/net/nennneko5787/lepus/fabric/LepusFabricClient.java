package net.nennneko5787.lepus.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * The client half of the Fabric entry point. SC-170 §5.
 *
 * <p>A separate entry point rather than a side check inside the main one, so that nothing on a
 * dedicated server ever loads a class that mentions a renderer.
 *
 * <p><b>First person is not registered here</b>, and used to be. Drawing an attachable through a
 * {@code SpecialModelRenderer} put it where the item is; Bedrock poses it in player space, so the
 * seam moved to the hand render itself — {@code ItemInHandRendererMixin}, which needs no
 * registration because a mixin is applied at class load.
 */
@SpecImpl("SC-170#attachable/geometry")
public final class LepusFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // The layer that draws a held item's Bedrock attachable ON the player in third person,
        // which is where Bedrock draws an attachable at all. Through a per-version file because
        // Fabric API renamed the callback at 26.2 — see FabricAttachableLayer.
        FabricAttachableLayer.register();
    }
}
