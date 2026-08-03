package net.nennneko5787.lepus.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.render.AttachableContext;
import net.nennneko5787.lepus.runtime.registry.AddonItem;
import net.nennneko5787.lepus.runtime.registry.BoundAttachables;

/**
 * Draws a held Bedrock attachable in <b>first person</b>. SC-170 §5, SC-180 §3.4.2.
 *
 * <p><b>Why this is not the item's model.</b> First person was drawn by a
 * {@code SpecialModelRenderer} for a while, which puts the model where the ITEM is — the hand — and
 * a Bedrock character authored with its feet at zero hangs two and a half blocks below that. No
 * translation corrects it, because Bedrock does not draw an attachable at the hand in first person
 * either: it draws it in player space, and a pack's animations are composed against that space.
 *
 * <p>So this rebuilds player space against the camera. In first person there is no player model to
 * hang it on, which is exactly why the third-person layer cannot serve here: it never runs.
 *
 * <p><b>What a first-person animation is allowed to move is SC-180 §4.1's business, not this
 * file's.</b> Real packs condition one on {@code v.main_hand && c.is_first_person} and then name
 * bones their idle already names — under the composition rule the idle keeps those, and the
 * character stays where third person puts her. Getting that rule wrong here looked like a placement
 * bug in this file and was not.
 *
 * <p><b>Version-free and loader-free.</b> What differs is only where it is called from — NeoForge
 * has {@code RenderHandEvent}, Fabric API has no first-person hook on either supported version and
 * gets a mixin. Both arrive at the same point in the same frame with the same arguments.
 */
@SpecImpl(value = "SC-170#attachable/first_person",
        note = "Held hands only. A worn attachable is not drawn in first person; see SC-180 §3.4.2.")
public final class FirstPersonAttachables {

    /** Opaque white: the model's own texture carries the colour. */
    private static final int NO_TINT = 0xFFFFFFFF;

    private FirstPersonAttachables() {
    }

    /**
     * Submits the attachable for one hand, if that hand holds one.
     *
     * @param poseStack the first-person pose stack, which is <b>camera space</b> — see
     *                  {@link #toPlayerSpace}
     * @param hand      which slot, not which side. A pack reads it as {@code c.item_slot} and poses
     *                  the two differently
     */
    public static void submit(Player player, InteractionHand hand, ItemStack stack,
            PoseStack poseStack, SubmitNodeCollector collector, int light, float partialTick) {
        if (player == null || stack == null || stack.isEmpty() || BoundAttachables.isEmpty()) {
            return;
        }
        // Armour in a hand is still armour. Bedrock shows a worn attachable only once it is worn,
        // and this hook fires for whatever is in the hand — including a helmet being carried.
        if (AddonItem.wornRatherThanHeld(stack)) {
            return;
        }
        BoundAttachables.of(stack)
                // A vanilla item's attachable stops here, and that is a measurement: a Bedrock client
                // draws nothing in first person for one, whatever its animations ask for (SC-170
                // §5.2). Its own model keeps drawing in this view, which is why the generated
                // override blanks the third-person hands alone.
                .filter(BoundAttachables.Bound::inFirstPerson)
                .ifPresent(bound -> {
                    poseStack.pushPose();
                    toPlayerSpace(poseStack, player);
                    AttachableGeometry.submit(collector, poseStack, bound.texture(),
                            bound.geometry(),
                            // The SAME playback the third-person layer uses for this hand: the
                            // player and the slot name it, not the view. Looking down at your own
                            // hands must not restart an animation, and it did while the clock was
                            // one number for the whole client.
                            bound.poseAt(AttachablePlaybacks.of(player.getId(),
                                    hand == InteractionHand.MAIN_HAND ? "main_hand" : "off_hand"),
                                    // NOT told where the player is looking, and that is the same
                                    // fact as the space above. The model is fixed to the screen, so
                                    // a bone that aimed itself at the gaze would swing WITHIN a
                                    // model that is already following it. Bedrock's first-person
                                    // character does not turn her head at all; the third-person one
                                    // does, which is why the layer still passes these.
                                    AttachableContext
                                            .firstPerson(hand == InteractionHand.MAIN_HAND)
                                            // What the player is DOING, which this view needs as
                                            // much as the other: the corpus poses the first-person
                                            // hand differently while sneaking, and asks with
                                            // `query.is_sneaking` in the entry's own blend.
                                            .doing(WearerState.of(player)),
                                    // The wearer's own bones, at rest. A first-person view draws no
                                    // player model to read them from, and the space is not the
                                    // player's anyway — but they must still be SUPPLIED, because a
                                    // bone nobody claims is a bone a pack's animation keeps. §4.2.
                                    //
                                    // ZERO ANGLES, AND THAT IS NOW A MEASUREMENT. Vanilla's
                                    // first-person state writes `body` and `head` from
                                    // `q.target_*_rotation`, so the player's real angles were fed
                                    // in to find out what those answer here. The character then
                                    // SWUNG as the view turned, and the Bedrock client holds her
                                    // still. So the queries answer zero for a first-person player —
                                    // which §4.3 had reasoned and nothing had confirmed.
                                    //
                                    // What survives is the half turn on `head`: a constant, so the
                                    // frame that refuted the angles says nothing against it.
                                    WearerSkeleton.upright(0.0f, 0.0f)),
                            AttachableGeometry.IN_FIRST_PERSON,
                            light, OverlayTexture.NO_OVERLAY, NO_TINT);
                    poseStack.popPose();
                });
    }

    /**
     * Rebuilds the space the third-person layer draws in, against the camera.
     *
     * <p><b>The pose stack here is plain camera space</b>, and that is worth stating because it is
     * not obvious from the call site: {@code GameRenderer.renderItemInHand} multiplies the stack by
     * the inverse of the matrix it then pushes onto the model-view stack, so the two cancel and what
     * is left is the view bob. Origin at the eye, −Z forward, +X right, +Y up.
     *
     * <p>Two things separate that from the space an attachable is posed in.
     *
     * <p><b>The origin.</b> Bedrock authors a character standing on y 0, so the eye has to come down
     * to the feet — read per frame rather than fixed at 1.62, because a crouching player's eye drops
     * and a model nailed to 1.62 would sink into the ground with them.
     *
     * <p><b>The heading, and this is the one that made first person wrong.</b> A player layer draws
     * in BODY space; the camera follows the HEAD. Minecraft lets the two differ by up to fifty
     * degrees and moves the body to catch up only when you walk, so a model left in camera space
     * swings off the player's back the moment the mouse moves — which is not "the same position as
     * third person" in any frame where the player is looking sideways. Undoing the difference is
     * what makes the two views agree.
     *
     * <p>Pitch is deliberately <b>not</b> undone. A player layer is not pitched either — Java tilts
     * the head bone, not the body — so leaving it means the model rides the camera's tilt, which is
     * the remaining honest divergence and is recorded in the coverage entry.
     */
    private static void toPlayerSpace(PoseStack poseStack, Player player) {
        // CAMERA SPACE, LEFT AS IT IS. No rotation is undone, so the model is fixed to the screen:
        // looking up and down does not move it, and neither does turning.
        //
        // That is Bedrock's behaviour and it was measured, not assumed — two of its screenshots, one
        // pitched fully up and one fully down, put the character's parts in the SAME screen
        // positions. It is also why a person watching it says the model "follows the view".
        //
        // <b>Undoing the camera was tried and was wrong.</b> Both compensations went in on the
        // premise that first person should hold the model where third person holds it, and both had
        // to come out: Bedrock renders the two views differently, and this one is not the player's
        // space at all.
        //
        // THE EYE HEIGHT IS THE RIGHT ORIGIN, and that is measured rather than assumed. Dropping by
        // the drawn model's own head instead — y 24 scaled, 1.40625 rather than 1.62 — was tried on
        // the arithmetic that the two differ by 0.21 blocks and that Y was the one axis of this
        // space never checked against a frame. It raised every first-person attachable by that much,
        // and the one whose first-person placement DOES match a Bedrock capture came back visibly
        // too high. Y is now checked and was already correct; Z was checked when this space was
        // derived and X against a capture. All three have been read off a frame.
        // NO PROJECTION CORRECTION, and one was tried on a real derivation. Java's hand pass
        // projects at a fixed 70° and Bedrock's default is 60, both measured to ignore the FOV
        // setting (SC-180 §4.4) - so scaling x and y by tan(35°)/tan(30°) should reproduce a 60°
        // frustum inside this one. On screen it made the one confirmed-matching character WORSE,
        // which refutes the premise: whatever Bedrock's fixed projection is, the difference to
        // this pass is not a vertical 60-versus-70. The sub-block residual on the other character
        // stays a recorded TODO rather than a number - five fitted constants have died in this
        // file, and now one derived one has too.
        poseStack.translate(0.0f, -player.getEyeHeight(), 0.0f);
        poseStack.scale(PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE);
    }

    /**
     * What vanilla draws a player model at, and therefore what the third-person layer draws inside.
     *
     * <p>{@code AvatarRenderer.scale} multiplies by this before any layer runs, so an attachable on
     * a player is 0.9375 of the size its numbers say. First person was drawing at 1.0 and was
     * therefore <b>6.7% larger than the same model on the same player seen from outside</b> — small
     * enough to read as "the scale is wrong" without suggesting a number, which is how it was
     * reported.
     *
     * <p>Read out of the class rather than remembered: it is vanilla's constant, not ours.
     */
    private static final float PLAYER_MODEL_SCALE = 0.9375f;
}
