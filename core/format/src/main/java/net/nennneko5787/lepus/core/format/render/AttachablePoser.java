package net.nennneko5787.lepus.core.format.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.lepus.core.molang.MolangContext;
import net.nennneko5787.lepus.core.molang.MolangExpr;

/**
 * What an attachable looks like right now: which animations play, and the pose they make.
 * SC-170 §5, SC-180 §4, SC-130 §4.
 *
 * <p><b>{@code scripts.animate} is a list of amounts, not a list of animations.</b> An entry may be
 * a bare name, or an object whose one entry carries a Molang expression — and that expression is
 * <em>how much</em> of the animation to apply, not whether to apply it. Mojang: "the query in the
 * scripts section is only a blend value for the animation. It defines 'how much' the animation
 * plays, not when it plays and when it doesn't." Reading it as a switch is right for a condition
 * that only ever answers zero or one, which is what the corpus writes, and wrong for every vanilla
 * entry that blends a walk by {@code query.modified_move_speed}. SC-180 §4.1.1.
 *
 * <p><b>{@code pre_animation} runs first, every frame.</b> Its statements assign to {@code v.} —
 * {@code v.main_hand = c.item_slot == 'main_hand';} — and the conditions then read those variables.
 * Evaluating the conditions without running it first leaves every such variable at zero, which
 * silently answers "false" to every question a pack asks about itself.
 *
 * <p><b>Two animations that name the same bone both get it.</b> Their channel components add, in
 * order, and a transform is built once at the end — each scaled by its own blend. SC-180 §4.1,
 * where the three rules that were tried instead are recorded along with what each looked like on
 * screen.
 */
@SpecImpl({"SC-170#attachable/scripts", "SC-180#animation/bones"})
public final class AttachablePoser {


    /**
     * The wearer's bones as first person has them, for a player standing still. SC-180 §4.2.1.
     *
     * <p><b>Lives in the half of the build with no Minecraft in it so the renderer and the measuring
     * tool cannot disagree about it.</b> They did, three times in one day: the survey quietly posed
     * these at identity while the renderer turned them, and every extent it printed for a
     * `body`-parented attachable was a frame nobody was drawing. Whatever is here, both read it.
     *
     * <p><b>Half a turn on `body`, and it is a measurement.</b> Probe v10
     * ({@code spec/features/0005-attachables/probe/}) put an animation-less `body`-parented rig on
     * the Bedrock client in first person: it is VISIBLE, its +X marker on the screen's right, its
     * −X marker on the left, its forward marker straddling the camera — the whole rig turned half a
     * turn about the player's centre. The `waist`-parented character never sees this bone, which is
     * why she was correct all along and could never testify about it.
     *
     * <p>`head`'s half turn is vanilla's own — `base_pose` writes
     * `[q.target_x_rotation, q.target_y_rotation + 180, 0]`. `body` was identity here for as long
     * as this map existed, on the reading that those queries answer zero in first person; the probe
     * says the engine turns `body` regardless, which base_pose expresses if `q.target_y_rotation`
     * answers 180 there rather than 0. The mechanism is a guess; the half turn is not.
     */
    public static final Map<String, Mat4f> FIRST_PERSON_WEARER = Map.of(
            "body", Mat4f.rotationY(180.0f),
            "head", Mat4f.rotationY(180.0f));

    /** One entry of {@code scripts.animate}: something to play, and how much of it. */
    private record Track(Playable animation, Optional<MolangExpr> when) {
    }

    private final GeometryIr geometry;
    private final List<Track> tracks;
    private final List<MolangExpr> preAnimation;

    /**
     * @param animations   in {@code scripts.animate} order, each with its blend expression's source
     *                     or empty. An entry may be an animation or an animation CONTROLLER — a pack
     *                     writes both in the same list and looks both up in the same map
     * @param preAnimation the {@code scripts.pre_animation} statements, in order
     */
    public AttachablePoser(GeometryIr geometry,
            List<Map.Entry<Playable, Optional<String>>> animations,
            List<String> preAnimation) {
        this.geometry = geometry;
        this.tracks = new ArrayList<>();
        for (Map.Entry<Playable, Optional<String>> entry : animations) {
            tracks.add(new Track(entry.getKey(), entry.getValue().map(AttachablePoser::compile)));
        }
        this.preAnimation = preAnimation.stream().map(AttachablePoser::compile).toList();
    }

    /**
     * Every bone's transform at a moment, for the contexts this frame is in.
     *
     * <p><b>Every animation that names a bone contributes to it, by its blend.</b> SC-180 §4.1.
     */
    public Map<String, Mat4f> at(Playback playback, MolangContext context) {
        return at(playback, context, Map.of());
    }

    /**
     * As above, with bones the WEARER drives rather than the pack. SC-180 §4.2.
     *
     * <p>A Bedrock attachable names bones after the player's own — a halo's geometry is a cube-less
     * {@code head} at the player's head pivot with the ring hanging off it — and Bedrock drives
     * those from the player's skeleton. Without that a halo is a ring that stays where the model
     * declared it while the head turns underneath, which is how it was reported.
     *
     * <p><b>The wearer's transform goes OUTSIDE the pack's, not instead of it.</b> A pack may pose a
     * wearer-named bone and Bedrock honours it: one character's first-person animation moves the
     * cube-less {@code body} bone she hangs off by {@code [0, -1, -6]}, and that third of a block
     * forward is what carries her head in FRONT of the first-person camera instead of behind it.
     * Replacing instead of composing left the head entirely behind the near plane, which on screen
     * is a character with no head at all.
     *
     * <p>It reached the head and nothing else because the other character in the same pack hangs off
     * {@code waist}, which no wearer drives — the one asymmetry in the corpus that lets a single
     * change affect one of them and not the other.
     *
     * @param skeleton bone name → the wearer's transform for it, in Bedrock's space
     */
    public Map<String, Mat4f> at(Playback playback, MolangContext context,
            Map<String, Mat4f> skeleton) {
        // `pre_animation` FIRST, as documented — its assignments are what the conditions read.
        //
        // Evaluating the conditions ahead of it was tried, to explain why the corpus's first-person
        // animation does not show on the Bedrock client. It explains too much: neither character's
        // would play, and one of them demonstrably IS posed by hers there. What separates them is
        // the `loop` field, in AnimationSampler.
        for (MolangExpr statement : preAnimation) {
            statement.evaluate(context);
        }
        // A CONDITION IS AN AMOUNT, NOT A SWITCH. SC-180 §4.1.1. Mojang: "the query in the scripts
        // section is only a blend value for the animation. It defines 'how much' the animation
        // plays, not when it plays and when it doesn't." A bare entry is a blend of one; an entry
        // with an expression is a blend of whatever that expression answers, which for the
        // corpus's `v.main_hand && c.is_first_person` is still zero or one, and for vanilla's
        // `query.modified_move_speed` is a walk that grows with the walking.
        float[] blend = new float[tracks.size()];
        for (int track = 0; track < tracks.size(); track++) {
            Optional<MolangExpr> when = tracks.get(track).when();
            blend[track] = when.isEmpty() ? 1.0f : when.get().evaluate(context);
        }
        // PER CHANNEL, ADDITIVELY, IN ORDER — and the transform built once at the end. SC-180 §4.1.
        // Bedrock's own documentation: "the skeleton is reset to its default pose ... then
        // animations are applied per-channel-additively in order", and "the channels (x, y, and z)
        // are added separately across animations first, then converted to a transform once all
        // animations have been cumulatively applied". A matrix per animation cannot express that,
        // which is what this used to build before picking one of them.
        Map<String, AnimationSampler.Channels> channels = new LinkedHashMap<>();
        for (int track = 0; track < tracks.size(); track++) {
            tracks.get(track).animation().accumulate(playback, context, blend[track], channels);
        }
        Map<String, Mat4f> extra = new LinkedHashMap<>();
        channels.forEach((bone, accumulated) -> extra.put(bone, accumulated.transform()));
        // A bone both the pack and the wearer pose combines AS THE CHANNELS WOULD: the wearer's
        // contribution is one more animation in the stack (vanilla's base_pose IS one), so a pack
        // position and a wearer rotation build one transform, translation outermost - §4.1, and
        // the same v9 probe that pinned that order. Composing the other way round (driver outside)
        // sent the corpus's `body [0,-1,-6]` BACKWARDS the moment the wearer's half turn arrived.
        skeleton.forEach((bone, driven) ->
                extra.merge(bone, driven, (animated, driver) -> animated.times(driver)));
        if (extra.isEmpty()) {
            return BoneMatrices.bindPose(geometry);
        }
        return BoneMatrices.posed(geometry, bone -> Optional.ofNullable(extra.get(bone.name())));
    }

    /**
     * A pack's expression, compiled, or a zero that never throws.
     *
     * <p>Constitution rule 5 in the place it matters most: this is evaluated inside a render pass,
     * and an expression that refuses to compile there is not a diagnostic but a client that stops
     * drawing. A condition that will not compile answers false, which costs that one animation.
     */
    private static MolangExpr compile(String source) {
        try {
            return MolangExpr.compile(source);
        } catch (RuntimeException unparsed) {
            return MolangExpr.zero();
        }
    }
}
