package net.nennneko5787.lepus.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.item.ItemStack;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.render.AttachableContext;
import net.nennneko5787.lepus.core.format.render.Mat4f;
import net.nennneko5787.lepus.runtime.registry.AddonItem;
import net.nennneko5787.lepus.runtime.registry.BoundAttachables;

/**
 * Draws a held Bedrock attachable <b>on the player</b>. SC-170 §5, SC-180 §4.
 *
 * <p><b>Why this exists beside the special renderer.</b> A special model renderer draws at the
 * ITEM's position — the hand — and a Bedrock character model is authored with its feet at zero and
 * its head thirty units up, so it hangs two and a half blocks below the hand. That is not a number
 * to tune: Bedrock never draws an attachable at the hand. It attaches the model to the player and
 * lets the animation place it, which is why these files borrow the elytra's animation controller
 * and why their animations move a root bone at all.
 *
 * <p>So the seam is a player layer, exactly as the elytra's is. The special renderer keeps first
 * person, where there is no player model to hang anything on; the item definition's display-context
 * switch is what keeps the two from drawing the same model twice.
 *
 * <p>Version-free: {@code RenderLayer}, {@code AvatarRenderState} and {@code PlayerModel} are
 * byte-identical on 1.21.11 and 26.2, checked against both jars rather than assumed.
 */
@SpecImpl(value = "SC-170#attachable/geometry",
        note = "Third person: both hands and the four armour slots. First person is the special renderer.")
public final class AttachableLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    /** Opaque white: the model's own texture carries the colour. */
    private static final int NO_TINT = 0xFFFFFFFF;

    public AttachableLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light,
            AvatarRenderState state, float yRot, float xRot) {
        // Both hands, each told which SLOT it is — and the slot is not the side.
        //
        // A pack asks `v.main_hand = c.item_slot == 'main_hand';` in pre_animation and poses itself
        // differently for the two. Which physical hand that is depends on the player: Minecraft has
        // a main-arm setting, so a left-handed player's main hand is their left one. Deciding it by
        // "right is main" is right for most players and silently wrong for the rest.
        //
        // A stack that is ARMOUR is skipped here even while it sits in a hand. Bedrock shows a worn
        // attachable only once it is worn; drawing it from the hand as well put a whole character on
        // the player for merely selecting the item, and two of her once it was equipped.
        // What the WEARER's own skeleton is doing this frame, for the bones an attachable names
        // after the player's. Built once: six model parts read per draw would be the same six.
        Map<String, Mat4f> skeleton = WearerSkeleton.of(getParentModel());

        boolean rightIsMain = state.mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT;
        held(state.rightHandItemStack, AttachableContext.thirdPerson(rightIsMain).looking(xRot, yRot),
                skeleton, poseStack, collector, light);
        held(state.leftHandItemStack, AttachableContext.thirdPerson(!rightIsMain).looking(xRot, yRot),
                skeleton, poseStack, collector, light);

        // And what the player is WEARING. A Bedrock halo is an armour piece with an attachable, and
        // the item's own model is a flat icon — so a worn one drew nothing on the player and the
        // report was "the scale is not respected".
        //
        // The render state carries these as real ItemStacks, unlike `headItem` (the pumpkin-style
        // head slot) which is already resolved to a render state and cannot be read back. An
        // earlier note in this project said worn slots were unreachable; it was looking at the
        // wrong field.
        draw(state.headEquipment, AttachableContext.worn("head").looking(xRot, yRot),
                skeleton, poseStack, collector, light);
        draw(state.chestEquipment, AttachableContext.worn("chest").looking(xRot, yRot),
                skeleton, poseStack, collector, light);
        draw(state.legsEquipment, AttachableContext.worn("legs").looking(xRot, yRot),
                skeleton, poseStack, collector, light);
        draw(state.feetEquipment, AttachableContext.worn("feet").looking(xRot, yRot),
                skeleton, poseStack, collector, light);
    }

    /** A stack in a hand, which draws only when the pack did not declare it as something worn. */
    private void held(ItemStack stack, AttachableContext context, Map<String, Mat4f> skeleton,
            PoseStack poseStack, SubmitNodeCollector collector, int light) {
        if (stack == null || AddonItem.wornRatherThanHeld(stack)) {
            return;
        }
        draw(stack, context, skeleton, poseStack, collector, light);
    }

    private void draw(ItemStack stack, AttachableContext context, Map<String, Mat4f> skeleton,
            PoseStack poseStack, SubmitNodeCollector collector, int light) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        AddonItem.logicalIdOf(stack)
                .flatMap(BoundAttachables::at)
                .ifPresent(bound -> {
                    poseStack.pushPose();
                    toPlayerSpace(poseStack);
                    AttachableGeometry.submit(collector, poseStack, bound.texture(),
                            bound.geometry(),
                            bound.poseAt(AttachableClock.seconds(), context, skeleton),
                            AttachableGeometry.ON_PLAYER,
                            light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                            NO_TINT);
                    poseStack.popPose();
                });
    }

    /**
     * How far down Bedrock's floor is from where a player model's origin sits.
     *
     * <p>Java draws an entity model with <b>+Y pointing DOWN</b> and the origin a block and a half
     * up: a player's legs run from y 12 to y 24, and 24 is the ground. Bedrock authors the same
     * character with Y up from its feet. So Bedrock's y 0 belongs at Java's y 24.
     *
     * <p><b>The sign is the whole of it, and it was wrong the first time.</b> +Y being down means
     * this translation has to be POSITIVE to move the model down; it was negative, which lifted the
     * model by a block and a half instead of lowering it — and since the correction was also
     * missing, the character floated three blocks over the player's head. Exactly what the first
     * screenshot showed.
     */
    private static final float BEDROCK_FLOOR = 24.0f / 16.0f;

    private static void toPlayerSpace(PoseStack poseStack) {
        poseStack.translate(0.0f, BEDROCK_FLOOR, 0.0f);
    }
}
