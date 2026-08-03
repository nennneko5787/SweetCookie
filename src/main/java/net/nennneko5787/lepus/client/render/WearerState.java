package net.nennneko5787.lepus.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.render.AttachableContext;

/**
 * What the player carrying an attachable is doing, in the terms a pack asks in. SC-180 §5.
 *
 * <p><b>These six answers are what makes an animation controller do anything.</b> Its transitions
 * are questions about the wearer — sneaking, in water, swimming, gliding, sleeping, burning — and
 * every one of them read zero before, so a pack that ships a sneaking pose, a swimming pose and a
 * burning pose drew the standing one in all four situations.
 *
 * <p>Version-free: {@code Pose} has the same constants on 1.21.11 and 26.2, and
 * {@code LivingEntityRenderState} the same fields, both checked against the jars rather than
 * assumed. So is every method used here on {@code Player}.
 *
 * <p><b>Two sources, because the two views have two.</b> Third person reads a render state, which is
 * the interpolated snapshot the frame is drawn from and is all a layer is given for another player.
 * First person hooks the hand render, which is handed the player itself. Reading the entity in both
 * would be wrong for the first — the state is what everything else in that frame agrees with.
 */
@SpecImpl("SC-180#animation_controller/transitions")
public final class WearerState {

    private WearerState() {
    }

    /** The wearer as a render state has them: another player, or this one seen in third person. */
    public static AttachableContext.Wearer of(LivingEntityRenderState state) {
        return new AttachableContext.Wearer(
                state.hasPose(Pose.CROUCHING),
                state.isInWater,
                state.hasPose(Pose.SWIMMING),
                state.hasPose(Pose.FALL_FLYING),
                state.hasPose(Pose.SLEEPING),
                // The fire OVERLAY, which is what a viewer sees and what the pack is reacting to.
                // An entity that is fire-immune burns without it, and a pack asking `query.is_onfire`
                // is asking about the picture.
                state.displayFireAnimation);
    }

    /** The wearer as the player themselves, for the view drawn down their own arms. */
    public static AttachableContext.Wearer of(Player player) {
        return new AttachableContext.Wearer(
                player.isCrouching(),
                player.isInWater(),
                player.isSwimming(),
                player.isFallFlying(),
                player.isSleeping(),
                player.isOnFire());
    }
}
