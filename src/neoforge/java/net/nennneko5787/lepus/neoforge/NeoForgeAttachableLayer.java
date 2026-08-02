package net.nennneko5787.lepus.neoforge;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.nennneko5787.lepus.client.render.AttachableLayer;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Puts the attachable layer on the player renderers. The NeoForge spelling. SC-170 §5.
 *
 * <p><b>Every skin gets one.</b> Minecraft has a renderer per player model — the slim arms and the
 * wide ones — and registering to one of them leaves half of all players without the model, which is
 * the kind of bug that reads as "it works on my machine". The event enumerates them, so nothing
 * here has to know how many there are.
 */
@SpecImpl("SC-170#attachable/geometry")
final class NeoForgeAttachableLayer {

    private NeoForgeAttachableLayer() {
    }

    static void addLayers(EntityRenderersEvent.AddLayers event) {
        event.getSkins().forEach(skin -> {
            AvatarRenderer<?> player = event.getPlayerRenderer(skin);
            if (player != null) {
                player.addLayer(new AttachableLayer(player));
            }
        });
    }
}
