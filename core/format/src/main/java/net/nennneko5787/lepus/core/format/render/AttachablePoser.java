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
 * <p><b>{@code scripts.animate} is a list of decisions, not a list of animations.</b> An entry may
 * be a bare name that always plays, or an object whose one entry plays <em>while a Molang expression
 * is true</em>. Playing only the unconditional ones is what a first pass does, and it is why a model
 * looked identical in first and third person when Bedrock shows two quite different things: the
 * entry that distinguishes them is exactly the conditional one.
 *
 * <p><b>{@code pre_animation} runs first, every frame.</b> Its statements assign to {@code v.} —
 * {@code v.main_hand = c.item_slot == 'main_hand';} — and the conditions then read those variables.
 * Evaluating the conditions without running it first leaves every such variable at zero, which
 * silently answers "false" to every question a pack asks about itself.
 *
 * <p><b>Two animations that name the same bone do not both get it.</b> The last one in the list
 * takes it whole. SC-180 §4.1, where the two rules that were tried instead are recorded along with
 * what each looked like on screen.
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
     * <p>Half a turn on each. `head`'s is vanilla's own — `base_pose` writes
     * `[q.target_x_rotation, q.target_y_rotation + 180, 0]`, and those queries answer zero for a
     * first-person player (§4.3, measured). `body`'s is §4.2.1's, and is what puts the corpus's
     * `root2 position [-32, …]` on the side the Bedrock client shows.
     */
    public static final Map<String, Mat4f> FIRST_PERSON_WEARER = Map.of(
            "body", Mat4f.IDENTITY,
            "head", Mat4f.rotationY(180.0f));

    /** One entry of {@code scripts.animate}: something to play, and when. */
    private record Track(AnimationSampler animation, Optional<MolangExpr> when) {
    }

    private final GeometryIr geometry;
    private final List<Track> tracks;
    private final List<MolangExpr> preAnimation;

    /**
     * @param animations   in {@code scripts.animate} order, each with its condition's source or empty
     * @param preAnimation the {@code scripts.pre_animation} statements, in order
     */
    public AttachablePoser(GeometryIr geometry,
            List<Map.Entry<AnimationSampler, Optional<String>>> animations,
            List<String> preAnimation) {
        this.geometry = geometry;
        this.tracks = new ArrayList<>();
        for (Map.Entry<AnimationSampler, Optional<String>> entry : animations) {
            tracks.add(new Track(entry.getKey(), entry.getValue().map(AttachablePoser::compile)));
        }
        this.preAnimation = preAnimation.stream().map(AttachablePoser::compile).toList();
    }

    /**
     * Every bone's transform at a moment, for the contexts this frame is in.
     *
     * <p><b>A bone belongs to the LAST animation in {@code scripts.animate} that names it.</b>
     * SC-180 §4.1.
     */
    public Map<String, Mat4f> at(float seconds, MolangContext context) {
        return at(seconds, context, Map.of());
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
    public Map<String, Mat4f> at(float seconds, MolangContext context,
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
        boolean[] plays = new boolean[tracks.size()];
        for (int track = 0; track < tracks.size(); track++) {
            Optional<MolangExpr> when = tracks.get(track).when();
            plays[track] = when.isEmpty() || when.get().evaluate(context) != 0f;
        }
        // PER CHANNEL, ADDITIVELY, IN ORDER — and the transform built once at the end. SC-180 §4.1.
        // Bedrock's own documentation: "the skeleton is reset to its default pose ... then
        // animations are applied per-channel-additively in order", and "the channels (x, y, and z)
        // are added separately across animations first, then converted to a transform once all
        // animations have been cumulatively applied". A matrix per animation cannot express that,
        // which is what this used to build before picking one of them.
        Map<String, AnimationSampler.Channels> channels = new LinkedHashMap<>();
        for (int track = 0; track < tracks.size(); track++) {
            if (!plays[track]) {
                continue;
            }
            tracks.get(track).animation().accumulate(seconds, context, channels);
        }
        Map<String, Mat4f> extra = new LinkedHashMap<>();
        channels.forEach((bone, accumulated) -> extra.put(bone, accumulated.transform()));
        skeleton.forEach((bone, driven) ->
                extra.merge(bone, driven, (animated, driver) -> driver.times(animated)));
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
