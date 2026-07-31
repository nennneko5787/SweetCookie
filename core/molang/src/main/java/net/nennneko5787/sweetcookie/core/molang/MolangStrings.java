package net.nennneko5787.sweetcookie.core.molang;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * Interns Molang string literals to {@code float} identities.
 *
 * <p>Molang is float-typed and a string literal only ever meets {@code ==} and {@code !=} —
 * {@code query.get_equipped_item_name == 'wizardry:wand'}. Giving each distinct string a stable
 * number makes those comparisons work with no second value type, and anything arithmetic applied to
 * a string is as meaningless here as it is on Bedrock.
 *
 * <p>Identities are per process and never persisted. Nothing writes one to disk or to the wire, so
 * SC-110 §10's determinism requirement is not engaged; if that ever changes, this becomes a hash
 * rather than a counter.
 *
 * <p>Counting from a large negative base keeps interned identities away from the small integers that
 * real Molang values occupy, so a string accidentally compared against a number cannot collide.
 */
@SpecImpl("SC-130")
public final class MolangStrings {

    private static final float BASE = -1.0e7f;

    private static final Map<String, Float> IDS = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT = new AtomicInteger();

    private MolangStrings() {
    }

    /** The identity of {@code text}, allocating one on first sight. Case-sensitive (SC-130 §2.1). */
    public static float intern(String text) {
        return IDS.computeIfAbsent(text, ignored -> BASE - NEXT.getAndIncrement());
    }
}
