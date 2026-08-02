package net.nennneko5787.lepus.core.format.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.animation.AnimationIr;
import net.nennneko5787.lepus.core.molang.MolangContext;
import net.nennneko5787.lepus.core.molang.MolangExpr;

/**
 * An animation at a moment: what each bone is doing right now. SC-180 §4.
 *
 * <p>Feeds {@code BoneMatrices.posed}, whose hook exists for exactly this. Everything that decides
 * how a model moves lives here and is testable without a client: which keyframes bracket a time,
 * how a value between them is found, what a looping animation does past its end, and the order the
 * three channels compose in.
 *
 * <p><b>Compiled once, evaluated per frame.</b> A Molang component is compiled the first time it is
 * sampled and kept — parsing an expression sixty times a second per bone per channel is the kind of
 * cost that does not show up in a profile as one line, and the compiler already folds constants
 * (SC-130 §6), so a folded expression costs a field read.
 */
@SpecImpl("SC-180#animation/bones")
public final class AnimationSampler {

    private final AnimationIr animation;
    private final Map<String, MolangExpr> compiled = new LinkedHashMap<>();

    public AnimationSampler(AnimationIr animation) {
        this.animation = animation;
    }

    /**
     * Every animated bone's local transform at {@code seconds}.
     *
     * <p>Only the bones the animation names. A bone it says nothing about must be absent rather than
     * identity: the caller composes this over the model's own bind pose, and an identity written for
     * an unmentioned bone would erase whatever that bone was declared with.
     */
    public Map<String, Mat4f> at(float seconds, MolangContext context) {
        float time = timeIn(seconds);
        Map<String, Mat4f> out = new LinkedHashMap<>();
        animation.bones().forEach((bone, channels) -> {
            float[] nothing = new float[3];
            Optional<float[]> rotation = value(channels.rotation(), time, context, nothing);
            Optional<float[]> position = value(channels.position(), time, context, nothing);
            Optional<float[]> scale = value(channels.scale(), time, context, nothing);
            if (rotation.isEmpty() && position.isEmpty() && scale.isEmpty()) {
                return;
            }
            out.put(bone, transform(rotation, position, scale));
        });
        return out;
    }

    /**
     * One bone's three channels as numbers, mid-accumulation. SC-180 §4.1.
     *
     * <p>Bedrock adds animations <b>per channel component</b> and builds a transform only once every
     * animation has contributed — "the channels (x, y, and z) are added separately across animations
     * first, then converted to a transform once all animations have been cumulatively applied".
     * A matrix per animation cannot express that, which is why this carries numbers.
     *
     * <p>Rotation and position start at zero and scale at one, because that is the bind pose the
     * documentation says the skeleton is reset to at the top of each frame.
     */
    public static final class Channels {

        final float[] rotation = new float[3];
        final float[] position = new float[3];
        final float[] scale = {1.0f, 1.0f, 1.0f};

        /** This bone's accumulated channels as the one transform the frame draws. */
        public Mat4f transform() {
            return AnimationSampler.transform(
                    Optional.of(rotation), Optional.of(position), Optional.of(scale));
        }
    }

    /**
     * Adds this animation's contribution to what earlier ones left. SC-180 §4.1.
     *
     * <p><b>{@code this} is the accumulated value, and that is the whole point.</b> Molang's
     * {@code this} means "the value this expression will ultimately write to", and in an additive
     * system it is what the animations before this one have already put there. It is also why the
     * corpus writes {@code query.target_x_rotation - 110.0 - this} on every bone it aims: subtracting
     * the accumulation and adding the result is how a pack says <em>set</em> in a system that only
     * adds. Answering zero for {@code this} silently turns every one of those into an offset.
     *
     * <p>Scale multiplies rather than adds. The documentation says "per-channel-additively" without
     * separating scale out, and adding two scale channels would make a bone that two animations both
     * leave alone twice its size — the corpus never has two animations scale one bone, so nothing
     * here can tell the two apart. `TODO(SC-180)`.
     */
    public void accumulate(float seconds, MolangContext context, Map<String, Channels> into) {
        if (finishedAndGone()) {
            return;
        }
        float time = timeIn(seconds);
        animation.bones().forEach((bone, channels) -> {
            if (channels.rotation().isEmpty() && channels.position().isEmpty()
                    && channels.scale().isEmpty()) {
                return;
            }
            Channels carried = into.computeIfAbsent(bone, name -> new Channels());
            add(channels.rotation(), time, context, carried.rotation);
            add(channels.position(), time, context, carried.position);
            multiply(channels.scale(), time, context, carried.scale);
        });
    }

    private void add(Optional<AnimationIr.Channel> channel, float time, MolangContext context,
            float[] carried) {
        value(channel, time, context, carried).ifPresent(by -> {
            for (int axis = 0; axis < 3; axis++) {
                carried[axis] += by[axis];
            }
        });
    }

    private void multiply(Optional<AnimationIr.Channel> channel, float time, MolangContext context,
            float[] carried) {
        value(channel, time, context, carried).ifPresent(by -> {
            for (int axis = 0; axis < 3; axis++) {
                carried[axis] *= by[axis];
            }
        });
    }

    /**
     * The three channels as one transform: <b>translate, then rotate, then scale</b>.
     *
     * <p>The order is what makes an animated joint behave: the rotation is innermost so it turns the
     * bone about its own pivot — {@code BoneMatrices} puts this inside the pivot for that reason —
     * and the translation moves the turned bone rather than turning an already-moved one.
     *
     * <p>Rotation composes Z, Y, X, matching the order a bone's own declared rotation uses. Two
     * different orders in one model would be a bug nothing could see until an animation turned a
     * bone about two axes at once.
     */
    private static Mat4f transform(Optional<float[]> rotation, Optional<float[]> position,
            Optional<float[]> scale) {
        Mat4f matrix = Mat4f.IDENTITY;
        if (position.isPresent()) {
            float[] at = position.get();
            matrix = matrix.times(Mat4f.translation(at[0], at[1], at[2]));
        }
        if (rotation.isPresent()) {
            float[] by = rotation.get();
            // Through BoneMatrices, so an animated rotation and a declared one cannot disagree
            // about which way round Bedrock's angles go. They did, and the animation was the half
            // that showed it.
            matrix = matrix.times(BoneMatrices.rotate(by[0], by[1], by[2]));
        }
        if (scale.isPresent()) {
            float[] by = scale.get();
            matrix = matrix.times(Mat4f.scale(by[0], by[1], by[2]));
        }
        return matrix;
    }

    /**
     * Where in the animation {@code seconds} falls.
     *
     * <p>A looping animation wraps; one that does not hold at its end. An animation with no declared
     * length runs at the time it is given — a pack that writes only constants has no length and
     * needs none, and wrapping against a length of zero would divide by it.
     */
    /**
     * An animation that has already ended and contributes nothing. SC-180 §4.1.
     *
     * <p><b>A pack that writes only constants has no keyframes, so its length is zero</b> — Mojang:
     * "a single key frame is created at t=0.0 and all channel data is stored within that key frame",
     * and `animation_length` defaults to "time of last key frame". Such an animation finishes the
     * instant it starts. What happens next is the `loop` field's business: `true` restarts it, which
     * for a zero-length animation is an animation that ends on every frame; `hold_on_last_frame`
     * keeps the final pose forever.
     *
     * <p><b>This is the whole difference between the two characters of the corpus.</b> Both hang a
     * first-person animation off the same condition and both conditions read true; one carries
     * `loop: "hold_on_last_frame"` — the only occurrence in thirty-two files — and the other
     * `loop: true`. On the Bedrock client the first character IS posed by hers (at the screen's
     * edge, turned side-on, scaled up: her animation's numbers exactly) and the second is not (she
     * would spin half a turn, and does not). Every other candidate was eliminated first: the hand
     * she is held in (tested both), `c.item_slot` (vanilla's shield uses it), `c.is_first_person`,
     * and the composition rule itself (documented as additive).
     *
     * <p>`TODO(SC-180)`: the IR collapses `hold_on_last_frame` to `loop: false`, so this cannot yet
     * tell it from a plain non-looping animation — which Bedrock would also end, but by removing its
     * pose rather than holding it. The corpus has no such animation, so nothing here can see the
     * difference; a tri-state belongs in `AnimationIr` before one does.
     */
    private boolean finishedAndGone() {
        return animation.loop() && animation.length().orElse(0.0f) <= 0.0f;
    }

    private float timeIn(float seconds) {
        float length = animation.length().orElse(0.0f);
        if (length <= 0.0f) {
            return seconds;
        }
        if (!animation.loop()) {
            return Math.min(seconds, length);
        }
        float wrapped = seconds % length;
        return wrapped < 0.0f ? wrapped + length : wrapped;
    }

    /**
     * One channel's value at a time, interpolated between the keyframes that bracket it.
     *
     * <p>Linear, and <b>held at both ends</b>: before the first keyframe and after the last, the
     * nearest one answers. Bedrock's other interpolations — {@code catmullrom}, and the step a
     * {@code pre}/{@code post} pair makes — are recorded in the ledger rather than approximated
     * here, because an approximation that is right most of the time is the hardest kind to find.
     */
    private Optional<float[]> value(Optional<AnimationIr.Channel> channel, float time,
            MolangContext context, float[] carried) {
        if (channel.isEmpty()) {
            return Optional.empty();
        }
        NavigableMap<Float, AnimationIr.Keyframe> frames = channel.get().keyframes();
        Map.Entry<Float, AnimationIr.Keyframe> before = frames.floorEntry(time);
        Map.Entry<Float, AnimationIr.Keyframe> after = frames.ceilingEntry(time);
        if (before == null) {
            return Optional.of(evaluate(after.getValue(), context, carried));
        }
        if (after == null || before.getKey().equals(after.getKey())) {
            return Optional.of(evaluate(before.getValue(), context, carried));
        }
        float span = after.getKey() - before.getKey();
        float t = span <= 0.0f ? 0.0f : (time - before.getKey()) / span;
        float[] from = evaluate(before.getValue(), context, carried);
        float[] to = evaluate(after.getValue(), context, carried);
        return Optional.of(new float[] {
                from[0] + (to[0] - from[0]) * t,
                from[1] + (to[1] - from[1]) * t,
                from[2] + (to[2] - from[2]) * t});
    }

    /**
     * One keyframe's three components, with any Molang among them evaluated.
     *
     * <p><b>Each component is evaluated against its own {@code this}</b> — the value that component
     * already carries — because that is what Molang's {@code this} means: "the current value that
     * this expression will ultimately write to". The corpus aims arms with it:
     * {@code query.target_x_rotation - 110.0 - this} is relative to wherever the animations below
     * left the arm.
     *
     * <p>{@link #accumulate} supplies what the animations before this one left on that component;
     * {@link #at} supplies zero, because it applies one animation onto a bind pose and there is
     * nothing below it.
     */
    private float[] evaluate(AnimationIr.Keyframe keyframe, MolangContext context,
            float[] carried) {
        float[] out = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            AnimationIr.Component component = keyframe.components().get(axis);
            if (component.number().isPresent()) {
                out[axis] = component.number().get();
                continue;
            }
            out[axis] = expression(component.molang().orElse("0"))
                    .evaluate(new Carrying(context, carried[axis]));
        }
        return out;
    }

    /**
     * A context that answers {@code this} and delegates everything else.
     *
     * <p>A wrapper rather than a field on the sampler, because {@code this} differs per component
     * within one evaluation and a context is what an expression is handed.
     */
    private record Carrying(MolangContext delegate, float value) implements MolangContext {

        @Override
        public boolean isDefined(Scope scope, String name) {
            return delegate.isDefined(scope, name);
        }

        @Override
        public float read(Scope scope, String name) {
            return delegate.read(scope, name);
        }

        @Override
        public void write(Scope scope, String name, float written) {
            delegate.write(scope, name, written);
        }

        @Override
        public float call(Scope scope, String name, float[] arguments) {
            return delegate.call(scope, name, arguments);
        }

        @Override
        public net.nennneko5787.lepus.core.molang.MolangMath math() {
            return delegate.math();
        }

        @Override
        public float thisValue() {
            return value;
        }
    }

    /**
     * The compiled form of one component, or a zero that never throws.
     *
     * <p><b>An expression a pack wrote must not be able to end a frame.</b> Constitution rule 5, and
     * the sharpest case of it in this file: this runs sixty times a second inside a render pass, and
     * one unparsed expression there is not a diagnostic, it is a client that stops drawing.
     */
    private MolangExpr expression(String source) {
        return compiled.computeIfAbsent(source, text -> {
            try {
                return MolangExpr.compile(text);
            } catch (RuntimeException unparsed) {
                unreadable.add(text);
                return MolangExpr.zero();
            }
        });
    }

    /** Expressions this could not compile. For a caller that wants to report them once. */
    private final java.util.Set<String> unreadable = new java.util.LinkedHashSet<>();

    /** What failed to compile, if anything. Empty for every animation in the surveyed corpus. */
    public java.util.Set<String> unreadableExpressions() {
        return java.util.Collections.unmodifiableSet(unreadable);
    }
}
