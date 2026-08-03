package net.nennneko5787.lepus.core.format.render;

import java.util.Map;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.molang.MolangContext;

/**
 * Something an entry of {@code scripts.animate} can name. SC-180 §4, §5.
 *
 * <p>Two things, and a pack does not distinguish them: {@code "hoshino"} and
 * {@code "default_controller"} sit side by side in the same list and are looked up in the same
 * {@code animations} map. One is an animation, the other a state machine over animations — and what
 * the caller wants from either is the same sentence: <em>add what you are doing right now to this
 * frame's channels, scaled by this much</em>.
 *
 * <p>So the poser holds these rather than samplers, and the difference between a pack whose
 * controller is shipped and one whose controller is Mojang's stays where it belongs: in whether the
 * name resolved.
 */
@SpecImpl({"SC-180#animation/bones", "SC-180#animation_controller/states"})
public interface Playable {

    /**
     * Adds this frame's contribution to what earlier entries left. SC-180 §4.1.
     *
     * @param playback the HOLDER's playback — when this began and, for a controller, which state it
     *                 is in. Not a bare time: an animation's clock starts when its blend first
     *                 becomes non-zero, which is a fact about the player carrying the item and not
     *                 about the animation, which everyone holding one shares. SC-180 §4.1.1
     * @param blend    how much of it applies — never a switch. SC-180 §4.1.1
     */
    void accumulate(Playback playback, MolangContext context, float blend,
            Map<String, AnimationSampler.Channels> into);

    /**
     * Molang this could not compile, for a caller that wants to report it once.
     *
     * <p>An expression that will not compile answers zero and costs one channel, which on screen is
     * a limb resting at its bind angle — indistinguishable from an animation that simply does not
     * move it. Everything that swallows one records it here.
     */
    default java.util.Set<String> unreadableExpressions() {
        return java.util.Set.of();
    }
}
