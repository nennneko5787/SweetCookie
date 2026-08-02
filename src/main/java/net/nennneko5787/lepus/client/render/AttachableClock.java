package net.nennneko5787.lepus.client.render;

import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.molang.MolangContext;

/**
 * What time it is, for an animation, and what the world can tell it. SC-180 §4.
 *
 * <p>Version-free and loader-free on purpose: both renderers ask the same two questions, and the
 * answers have nothing to do with either.
 */
@SpecImpl(value = "SC-180#animation/bones",
        note = "A wall clock and an empty Molang context. Both are stage-D's to replace.")
public final class AttachableClock {

    /**
     * When the client started, so the numbers stay small.
     *
     * <p>{@code nanoTime} counts from an arbitrary origin and a float loses precision as it grows;
     * an animation sampled at 1e9 seconds judders because consecutive frames round to the same
     * value. Measuring from a start this process chose keeps it in the range a float is exact in
     * for the length of a session.
     */
    private static final long STARTED = System.nanoTime();

    private AttachableClock() {
    }

    /**
     * Seconds since the client started.
     *
     * <p><b>A wall clock, not the game's.</b> That is wrong in the ways you would expect — it keeps
     * running while the game is paused, and it is not the same on two clients — and it is right
     * enough for an idle loop, which is all stage C plays. The game's own tick counter is what an
     * animation should follow, and reaching it means a per-version accessor; recorded rather than
     * done, because doing it here would be the third version-split file for a value nothing yet
     * compares between clients.
     */
    public static float seconds() {
        return (System.nanoTime() - STARTED) / 1_000_000_000.0f;
    }

    /**
     * The Molang context an animation is evaluated against.
     *
     * <p>Empty: every query reads zero. Real packs ask for {@code query.target_x_rotation} and
     * {@code query.life_time}, and answering those means binding queries to the entity holding the
     * item — SC-130 §5, and the same work stage D needs for its conditions. Until then a query
     * reading zero gives the pose the animation describes with the player standing still, which is
     * a defensible still frame rather than a wrong moving one.
     */
    public static MolangContext world() {
        return MolangContext.standalone();
    }
}
