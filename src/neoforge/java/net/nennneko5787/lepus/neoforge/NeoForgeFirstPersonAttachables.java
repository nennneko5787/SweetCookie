package net.nennneko5787.lepus.neoforge;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.nennneko5787.lepus.client.render.FirstPersonAttachables;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Draws a held attachable in first person, on NeoForge. SC-170 §5.
 *
 * <p><b>The loader API exists here and does not on Fabric</b>, which is the whole difference between
 * this file and {@code ItemInHandRendererMixin}. {@code RenderHandEvent} is fired from the same
 * place that mixin injects at, with the same arguments — pose stack, collector, hand, stack, light —
 * and its signature is byte-identical on 1.21.11 and 26.2 even though the vanilla method it comes
 * from was renamed between them. Reading it out of both jars is what let this stay one file.
 *
 * <p><b>Not cancelled.</b> The event's normal use is to replace the hand entirely; we are only
 * adding to the frame. Cancelling would take away the arm, and the item's own model is already
 * {@code minecraft:empty} in these contexts, so there is nothing of vanilla's to suppress.
 *
 * <p>The player comes from the client rather than the event, which does not carry one. It is the
 * local player by construction — first person is drawn for nobody else.
 */
@SpecImpl("SC-170#attachable/first_person")
final class NeoForgeFirstPersonAttachables {

    private NeoForgeFirstPersonAttachables() {
    }

    static void onRenderHand(RenderHandEvent event) {
        FirstPersonAttachables.submit(Minecraft.getInstance().player, event.getHand(),
                event.getItemStack(), event.getPoseStack(), event.getSubmitNodeCollector(),
                event.getPackedLight(), event.getPartialTick());
    }
}
