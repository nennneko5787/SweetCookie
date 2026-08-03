package net.nennneko5787.lepus.core.format.ir.animation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.value.Provenance;

/**
 * One {@code controller.animation.*}: a state machine over animations. SC-180 §5.
 *
 * <p><b>This is how a Bedrock pack says "when".</b> A condition in {@code scripts.animate} cannot —
 * SC-180 §4.1.1, it is a blend amount, and an animation it starts never restarts. A controller has
 * states instead: one is active, it plays a set of animations, and its transitions are tested in
 * order until one answers non-zero.
 *
 * <p><b>Every attachable in the surveyed corpus has one</b>, and until now every one of them was
 * skipped in silence. Two of the three in that corpus point at
 * {@code controller.animation.elytra.default}, which lives in Mojang's own resource pack and is not
 * in the add-on; the third <b>ships its own</b>, which is what makes this implementable at all
 * against real input.
 *
 * @param name         {@code controller.animation.<name>}, as the pack spells it
 * @param initialState the state entered when the entity loads. Bedrock's default is {@code default}
 * @param states       state name → what it plays and where it can go, in declaration order
 */
@SpecImpl("SC-180#animation_controller/states")
public record AnimationControllerIr(
        String name,
        String initialState,
        Map<String, State> states,
        Provenance provenance) {

    /** What Bedrock enters when a controller does not name an {@code initial_state}. */
    public static final String DEFAULT_STATE = "default";

    public AnimationControllerIr {
        states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }

    /**
     * One state: what plays while it is active, and what can end it.
     *
     * @param animations      short names from the entity's own {@code animations} map, each with an
     *                        optional Molang blend — the same shape as {@code scripts.animate}, and
     *                        the same meaning (SC-180 §4.1.1)
     * @param transitions     target state → the Molang guard, <b>in order</b>. "The first to return
     *                        non-zero is the state to transition to", so the order is the priority
     *                        and a map would lose it
     * @param blendTransition seconds to cross-fade out of this state, when the pack asks for one
     */
    public record State(List<Play> animations, List<Transition> transitions,
            Optional<Float> blendTransition) {

        public State {
            animations = List.copyOf(animations);
            transitions = List.copyOf(transitions);
        }
    }

    /**
     * One animation a state plays.
     *
     * <p>The blend is kept as <b>source text</b>, like every other Molang in the IR (SC-110 §7).
     *
     * @param name  the short name in the entity's {@code animations} map
     * @param blend the Molang source, or empty when the entry was a bare string, which is a blend
     *              of one
     */
    public record Play(String name, Optional<String> blend) {
    }

    /**
     * One way out of a state.
     *
     * @param to        the state to enter
     * @param condition the Molang guard, kept as source
     */
    public record Transition(String to, String condition) {
    }

    /** The state this starts in, if the pack declared one that exists. */
    public Optional<State> initial() {
        return Optional.ofNullable(states.get(initialState));
    }
}
