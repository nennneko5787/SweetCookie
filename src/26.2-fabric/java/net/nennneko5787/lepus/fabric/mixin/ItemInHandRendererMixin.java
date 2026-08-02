package net.nennneko5787.lepus.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.nennneko5787.lepus.client.render.FirstPersonAttachables;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a held attachable in first person, on Fabric. The 26.2 spelling. SC-170 §5.
 *
 * <p><b>A mixin because Fabric API has no first-person hook</b>, on either supported version — the
 * class lists of {@code fabric-rendering-v1} 16.2.10 and 25.3.2 were both read rather than assumed.
 * What it does offer is level rendering, and that is the wrong pass: it is world-anchored, and on
 * 1.21.11 its context hands out a buffer source rather than the {@code SubmitNodeCollector} the
 * geometry submits through. NeoForge has {@code RenderHandEvent}, which fires at exactly the point
 * this injects at, so the loaders differ in their seam and in nothing else.
 *
 * <p><b>Why this file has a 1.21.11 twin.</b> 26.2 renamed the method — {@code renderArmWithItem}
 * became {@code submitArmWithItem} — with a parameter list identical down to the order. Naming both
 * in one {@code @Inject} does work, and was tried; it makes the 1.21.11 remapper report
 * "cannot remap submitArmWithItem" on every build. A permanent expected warning is how a real one
 * gets missed, which this project has already paid for once, so the divergence gets the directory
 * that exists for it instead.
 *
 * <p>Injected at HEAD, before the arm and swing transforms, because an attachable is posed against
 * the player rather than against the hand and the swing must not carry it. Nothing is cancelled: the
 * item's own model is {@code minecraft:empty} in these contexts, so vanilla draws nothing here
 * anyway, and cancelling would take the arm away from every other item too.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void lepus$submitAttachable(AbstractClientPlayer player, float partialTick,
            float pitch, InteractionHand hand, float swingProgress, ItemStack stack,
            float equipProgress, PoseStack poseStack, SubmitNodeCollector collector, int light,
            CallbackInfo callback) {
        FirstPersonAttachables.submit(player, hand, stack, poseStack, collector, light, partialTick);
    }
}
