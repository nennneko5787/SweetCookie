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
 * @param name    {@code animation.<name>}, as the pack spells it
 * @param loop    whether it repeats. Bedrock also has {@code "hold_on_last_frame"}, kept as false
 *                here with the distinction recorded in the coverage ledger rather than guessed at
 * @param length  {@code animation_length} in seconds, absent when the pack states none
 * @param bones   bone name → what happens to it
 */
@SpecImpl("SC-180#animation/bones")
public record AnimationIr(
        String name,
        boolean loop,
        Optional<Float> length,
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

    /**
     * One value at one time: three components, each a number or a Molang expression.
     *
     * <p><b>Molang is kept as source text</b> (SC-110 §7): the IR must not hold something evaluable,
     * and the expressions in these files reach for the world — {@code query.target_x_rotation},
     * {@code query.life_time}, and {@code this}, which is the channel's own current value. They are
     * compiled where something is ready to run them, with provenance to report against.
     *
     * @param components exactly three, in x, y, z order
     */
    public record Keyframe(List<Component> components) {

        public Keyframe {
            if (components.size() != 3) {
                throw new IllegalArgumentException("a keyframe has three components");
            }
            components = List.copyOf(components);
        }

        /** The same number on all three axes, which is how Bedrock writes {@code "scale": 0}. */
        public static Keyframe of(float all) {
            return of(all, all, all);
        }

        public static Keyframe of(float x, float y, float z) {
            return new Keyframe(List.of(
                    Component.of(x), Component.of(y), Component.of(z)));
        }

        /** True when every component is a plain number, so nothing has to be evaluated. */
        public boolean isNumeric() {
            return components.stream().allMatch(component -> component.molang().isEmpty());
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
