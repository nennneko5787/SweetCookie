package net.nennneko5787.lepus.core.format.render;

import java.util.IdentityHashMap;
import java.util.Map;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * One holder's playback: when each animation began, and which state each controller is in.
 * SC-180 §4.1.1, §5.2.
 *
 * <p><b>An animation's time is per holder, not per animation.</b> Mojang, on a condition in
 * {@code scripts.animate}: "the animation will start playing once [the query] is true/1, but it will
 * never stop playing. It will just fade out once the value is false/0 again … <b>It won't play from
 * the start again.</b>" So the clock starts when an entry's blend first becomes non-zero, and from
 * then on it simply runs — which is a fact about this player carrying this item, not about the
 * animation, which is shared by everyone holding one.
 *
 * <p><b>What that was costing.</b> Everything was sampled from one clock measured from when the
 * client started. The corpus has a six-hundred-second animation whose head is written awake until
 * t≈300 and asleep from t≈301.75 to t≈599.2 — so the character fell asleep five minutes after the
 * game launched rather than five minutes after being picked up, and could be asleep the instant she
 * was first drawn. On screen that is a head at a fixed odd angle, which reads as a posing bug.
 *
 * <p>Keyed by <b>identity</b>: the {@link Playable}s are built once per bound attachable and shared
 * by every holder, so they are the natural name for "which animation", and two equal-looking
 * samplers are still two animations.
 */
@SpecImpl({"SC-180#animation/bones", "SC-180#animation_controller/states"})
public final class Playback {

    /** Never started. Distinct from zero, which is the frame an animation begins on. */
    public static final float NOT_STARTED = -1.0f;

    private final Map<Playable, Float> begun = new IdentityHashMap<>();
    private final Map<Playable, String> states = new IdentityHashMap<>();
    private float now;

    /** A playback whose clock reads zero: a still frame, for tests and the offline survey. */
    public Playback() {
    }

    public Playback(float seconds) {
        this.now = seconds;
    }

    /** Moves the shared clock to this frame. Monotonic; nothing here rewinds. */
    public void advanceTo(float seconds) {
        this.now = seconds;
    }

    public float now() {
        return now;
    }

    /**
     * How long this has been playing, starting its clock if it is playing right now.
     *
     * @param playing whether this frame's blend is non-zero. <b>Only the first such frame matters</b>
     *                — after it the clock runs whatever the blend does, which is what "it won't play
     *                from the start again" means
     * @return seconds since it began, or {@link #NOT_STARTED} if it never has
     */
    public float timeOf(Playable playable, boolean playing) {
        Float began = begun.get(playable);
        if (began == null) {
            if (!playing) {
                return NOT_STARTED;
            }
            begun.put(playable, now);
            return 0.0f;
        }
        return now - began;
    }

    /** The state a controller is in, or the one it starts in. */
    public String stateOf(Playable controller, String initial) {
        return states.getOrDefault(controller, initial);
    }

    /** Remembers a transition. */
    public void enter(Playable controller, String state) {
        states.put(controller, state);
    }
}
