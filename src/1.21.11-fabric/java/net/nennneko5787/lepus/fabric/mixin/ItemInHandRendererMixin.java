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
 * Draws a held attachable in first person, on Fabric. The 1.21.11 spelling. SC-170 §5.
 *
 * <p><b>The twin of the 26.2 file, and the whole difference is the method's name</b> —
 * {@code renderArmWithItem} here, {@code submitArmWithItem} there, with an identical parameter list.
 * That file carries the reasoning: why a mixin at all, why HEAD, and why one file naming both names
 * was rejected.
 *
 * <p>This node is the obfuscated one, so the name here is also the one the refmap has to resolve. A
 * name that does not exist in the mappings is reported by the remapper and not by the compiler,
 * which is why the split is worth its duplication.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void lepus$submitAttachable(AbstractClientPlayer player, float partialTick,
            float pitch, InteractionHand hand, float swingProgress, ItemStack stack,
            float equipProgress, PoseStack poseStack, SubmitNodeCollector collector, int light,
            CallbackInfo callback) {
        FirstPersonAttachables.submit(player, hand, stack, poseStack, collector, light, partialTick);
    }
}
