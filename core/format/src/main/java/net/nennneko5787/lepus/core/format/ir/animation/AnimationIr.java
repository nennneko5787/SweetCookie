package net.nennneko5787.lepus.core.format.ir.animation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.value.Provenance;

/**
 * One Bedrock animation: what each bone does over time. SC-180 §4.
 *
 * <p><b>Where a held model's position actually comes from.</b> An attachable has no placement of its
 * own — Bedrock positions it by animating its bones relative to the player, so
 * {@code animation.shiroko_onbu.hand} setting {@code root3} to {@code position [0, 0, -11]} is the
 * whole reason the model sits where it does in first person. Anything that tried to place it with an
 * item display transform instead would be fighting this file.
 *
 * @param name        {@code animation.<name>}, as the pack spells it
 * @param loop        whether it repeats. Bedrock also has {@code "hold_on_last_frame"}, kept as
 *                    false here with the distinction recorded in the coverage ledger rather than
 *                    guessed at
 * @param length      {@code animation_length} in seconds, absent when the pack states none
 * @param blendWeight {@code blend_weight}, absent when the pack states none, which Bedrock reads as
 *                    one. "How much this animation is blended with the others. 0.0 = off. 1.0 =
 *                    fully apply all transforms. Can be an expression" — so it is a number OR
 *                    Molang, which is exactly what a {@link Component} is
 * @param bones       bone name → what happens to it
 */
@SpecImpl("SC-180#animation/bones")
public record AnimationIr(
        String name,
        boolean loop,
        Optional<Float> length,
        Optional<Component> blendWeight,
        Map<String, Bone> bones,
        Provenance provenance) {

    public AnimationIr {
        bones = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(bones));
    }

    /**
     * One bone's three channels. Any of them may be absent, and most are.
     *
     * <p>Rotation is in degrees and applies about the bone's own pivot, exactly as the bone's
     * declared rotation does; position is in the model's units; scale is a multiplier.
     */
    public record Bone(Optional<Channel> rotation, Optional<Channel> position,
            Optional<Channel> scale) {
    }

    /**
     * How one channel moves over time.
     *
     * <p><b>A constant is a timeline of one.</b> Bedrock writes {@code "position": [0, 3, 0]} and
     * {@code "position": {"0.0": [...], "1.0": [...]}} for the same channel, and both mean "the value
     * at a time"; collapsing the first into the second at parse time means the sampler has one shape
     * to handle rather than two, and no caller has to ask which the pack wrote.
     *
     * @param keyframes by time in seconds, ascending. Never empty
     */
    public record Channel(java.util.NavigableMap<Float, Keyframe> keyframes) {

        public Channel {
            keyframes = Collections.unmodifiableNavigableMap(new TreeMap<>(keyframes));
        }

        /** A channel that never changes. */
        public static Channel constant(Keyframe value) {
            TreeMap<Float, Keyframe> single = new TreeMap<>();
            single.put(0.0f, value);
            return new Channel(single);
        }

        /** True when nothing moves, so a caller can skip interpolating three components. */
        public boolean isConstant() {
            return keyframes.size() == 1;
        }
    }

    /** How a channel moves INTO a keyframe and out of it. SC-180 §4.1.2. */
    public enum LerpMode {

        /** A straight line between the two values. Bedrock's default. */
        LINEAR,

        /** A spline through the neighbouring keyframes too, so the motion has no corners. */
        CATMULLROM
    }

    /**
     * One value at one time: three components, each a number or a Molang expression.
     *
     * <p><b>Molang is kept as source text</b> (SC-110 §7): the IR must not hold something evaluable,
     * and the expressions in these files reach for the world — {@code query.target_x_rotation},
     * {@code query.life_time}, and {@code this}, which is the channel's own current value. They are
     * compiled where something is ready to run them, with provenance to report against.
     *
     * <p><b>Two values, not one.</b> Bedrock lets a keyframe hold a {@code pre} and a {@code post} —
     * the value the channel arrives at and the value it leaves with — which is how an animation
     * steps instantly at one instant. They are equal for every keyframe that does not, which is
     * every keyframe of the surveyed corpus, and keeping them apart is what lets a segment ask for
     * "the value at the end of the incoming edge" without knowing whether the pack wrote one value
     * or two.
     *
     * @param pre  what the channel is worth arriving at this time; exactly three, in x, y, z order
     * @param post what it is worth leaving it; the same three when the pack wrote one value
     * @param lerp how the segment that STARTS here is drawn. Bedrock's {@code lerp_mode}, which sits
     *             on the keyframe rather than on the channel — and a segment is a curve when either
     *             of its two ends asks for one
     */
    public record Keyframe(List<Component> pre, List<Component> post, LerpMode lerp) {

        public Keyframe {
            if (pre.size() != 3 || post.size() != 3) {
                throw new IllegalArgumentException("a keyframe has three components");
            }
            pre = List.copyOf(pre);
            post = List.copyOf(post);
        }

        /** One value for both sides, interpolated linearly: what most keyframes are. */
        public Keyframe(List<Component> components) {
            this(components, components, LerpMode.LINEAR);
        }

        /** The same number on all three axes, which is how Bedrock writes {@code "scale": 0}. */
        public static Keyframe of(float all) {
            return of(all, all, all);
        }

        public static Keyframe of(float x, float y, float z) {
            return new Keyframe(List.of(
                    Component.of(x), Component.of(y), Component.of(z)));
        }

        /** True when the pack wrote two different values here, so the channel steps at this time. */
        public boolean steps() {
            return !pre.equals(post);
        }

        /** True when every component is a plain number, so nothing has to be evaluated. */
        public boolean isNumeric() {
            return pre.stream().allMatch(component -> component.molang().isEmpty())
                    && post.stream().allMatch(component -> component.molang().isEmpty());
        }
    }

    /**
     * One component of one keyframe: a number, or the Molang that computes it.
     *
     * @param number the value, when the pack wrote one
     * @param molang the source, when it wrote an expression instead
     */
    public record Component(Optional<Float> number, Optional<String> molang) {

        public static Component of(float value) {
            return new Component(Optional.of(value), Optional.empty());
        }

        public static Component of(String expression) {
            return new Component(Optional.empty(), Optional.of(expression));
        }
    }
}
