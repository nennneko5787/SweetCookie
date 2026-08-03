package net.nennneko5787.lepus.client.render;

import java.util.HashMap;
import java.util.Map;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.render.Playback;

/**
 * Every holder's playback, kept between frames. SC-180 §4.1.1, §5.2.
 *
 * <p><b>An animation's clock and a controller's state belong to the player carrying the item</b>, not
 * to the animation, which everyone holding one shares. Without somewhere to put them, everything was
 * sampled from one clock measured from when the client started — and the corpus has a
 * six-hundred-second animation that puts a character to sleep halfway through, so which half you saw
 * was decided by the game's uptime rather than by when you picked her up.
 *
 * <p>Keyed by <b>entity id and slot</b>. Both hands and the four armour slots are separate playbacks
 * on one player, because they are separate attachables whose animations start at different moments —
 * and `AvatarRenderState.id` carries the id on both 1.21.11 and 26.2, checked against the jars.
 *
 * <p><b>Swept, because a client meets more players than it keeps.</b> An entry not asked for in a
 * minute is dropped: a player who walks out of render distance must not hold a map entry for the
 * session, and one who walks back in starting their animations afresh is the same thing Bedrock does
 * when it stops and restarts drawing them.
 */
@SpecImpl({"SC-180#animation/bones", "SC-180#animation_controller/states"})
public final class AttachablePlaybacks {

    /** How long an untouched playback survives. Longer than any hitch, shorter than a session. */
    private static final float FORGET_AFTER_SECONDS = 60.0f;

    private record Holder(int entity, String slot) {
    }

    private record Kept(Playback playback, float[] lastSeen) {
    }

    private static final Map<Holder, Kept> KEPT = new HashMap<>();
    private static float sweptAt;

    private AttachablePlaybacks() {
    }

    /**
     * This holder's playback, advanced to now.
     *
     * <p>Called from the render thread only, which is why a plain map is enough — and the reason to
     * say so here rather than to reach for a concurrent one and imply otherwise.
     */
    public static Playback of(int entity, String slot) {
        float now = AttachableClock.seconds();
        sweep(now);
        Kept kept = KEPT.computeIfAbsent(new Holder(entity, slot),
                holder -> new Kept(new Playback(now), new float[] {now}));
        kept.lastSeen()[0] = now;
        kept.playback().advanceTo(now);
        return kept.playback();
    }

    /** Drops what nobody has drawn for a while. Runs at most once a second. */
    private static void sweep(float now) {
        if (now - sweptAt < 1.0f) {
            return;
        }
        sweptAt = now;
        KEPT.entrySet().removeIf(entry -> now - entry.getValue().lastSeen()[0] > FORGET_AFTER_SECONDS);
    }

    /** Forgets everything, for a world change or a pack reload. */
    public static void clear() {
        KEPT.clear();
    }
}
