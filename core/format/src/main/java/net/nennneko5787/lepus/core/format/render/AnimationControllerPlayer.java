package net.nennneko5787.lepus.core.format.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.animation.AnimationControllerIr;
import net.nennneko5787.lepus.core.molang.MolangContext;
import net.nennneko5787.lepus.core.molang.MolangExpr;

/**
 * A controller deciding, this frame, which of its states is active. SC-180 §5.
 *
 * <p><b>Where a Bedrock pack says "when".</b> A condition in {@code scripts.animate} cannot: it is
 * an amount, and an animation it starts never restarts (SC-180 §4.1.1). Sneaking, swimming, burning
 * and sleeping are all authored here in the surveyed corpus, and none of them has ever been drawn by
 * this build, because a name that resolved to a controller was skipped in silence.
 *
 * <p><b>No history, and that is a stated divergence rather than an oversight.</b> Bedrock's machine
 * remembers which state it is in between frames; this one resolves the machine from its initial
 * state every frame, following transitions until none fires or a state repeats. For a controller
 * whose guards are questions about the world right now — which is every controller in the corpus,
 * and Mojang's own elytra one — the two agree, because each state carries the transition back out
 * that its own guard's negation opens. They part company for anything that depends on how the entity
 * got here: {@code query.all_animations_finished}, {@code blend_transition}, and a state whose only
 * way in is a one-shot event. Holding the state per holder is the same work the animation clock
 * needs (§4.1.1), and it belongs with it.
 *
 * <p>The repeat check is what keeps a pack's own mistake from spinning here. One controller in the
 * corpus has a {@code walking} state whose way back is {@code !query.is_gliding} — true whenever the
 * player is not gliding, which is nearly always — so {@code default → walking → default} is a cycle
 * in the file itself. Bedrock takes one transition per frame and simply flickers; this stops on the
 * repeat and keeps the state it had reached.
 */
@SpecImpl({"SC-180#animation_controller/states", "SC-180#animation_controller/transitions"})
public final class AnimationControllerPlayer implements Playable {

    /** One state, with everything Molang in it compiled. */
    private record State(List<Map.Entry<Playable, Optional<MolangExpr>>> plays,
            List<Map.Entry<String, MolangExpr>> transitions) {
    }

    private final String initial;
    private final Map<String, State> states = new LinkedHashMap<>();
    private final Set<String> unreadable = new LinkedHashSet<>();

    /**
     * @param controller the parsed file
     * @param animations short name → what it plays, already resolved. A name this map does not have
     *                   is a state that plays nothing: Mojang's own elytra controller asks for
     *                   {@code default}, {@code gliding}, {@code sneaking}, {@code sleeping} and
     *                   {@code swimming}, and the corpus's attachables define two of those five —
     *                   the other three are meant to draw nothing, not to be an error
     */
    public AnimationControllerPlayer(AnimationControllerIr controller,
            Map<String, Playable> animations) {
        this.initial = controller.initialState();
        controller.states().forEach((name, state) -> {
            List<Map.Entry<Playable, Optional<MolangExpr>>> plays = new ArrayList<>();
            for (AnimationControllerIr.Play play : state.animations()) {
                Playable playable = animations.get(play.name());
                if (playable == null) {
                    continue;
                }
                plays.add(Map.entry(playable, play.blend().map(this::compile)));
            }
            List<Map.Entry<String, MolangExpr>> transitions = new ArrayList<>();
            for (AnimationControllerIr.Transition transition : state.transitions()) {
                transitions.add(Map.entry(transition.to(), compile(transition.condition())));
            }
            states.put(name, new State(plays, transitions));
        });
    }

    @Override
    public void accumulate(Playback playback, MolangContext context, float blend,
            Map<String, AnimationSampler.Channels> into) {
        State state = states.get(activeState(playback, context));
        if (state == null) {
            return;
        }
        for (Map.Entry<Playable, Optional<MolangExpr>> play : state.plays()) {
            float amount = play.getValue().map(expr -> expr.evaluate(context)).orElse(1.0f);
            play.getKey().accumulate(playback, context, blend * amount, into);
        }
    }

    /**
     * The state this frame is in, remembered from the last one and stepped at most once.
     *
     * <p><b>One transition per frame</b>, which is Bedrock's: the machine is where the last frame
     * left it and takes the first guard that fires. The transitions of a state are ordered and
     * <b>the first non-zero one wins</b> — Mojang: "the first to return non-zero is the state to
     * transition to". A map keyed by target would lose that, which is why the IR keeps a list.
     *
     * <p>Every state of every controller in the surveyed corpus is one hop from the initial one, so
     * a holder's first frame already lands in the right state and no situation takes two frames to
     * reach. What the memory buys is everything that is <em>not</em> a question about the world right
     * now — and the {@code walking} state whose way back is `!query.is_gliding`, a cycle in the
     * pack's own file, now flickers between two states exactly as it does on a Bedrock client
     * instead of being frozen at the repeat. Neither state draws anything.
     */
    /**
     * The state this holder is in, without stepping the machine.
     *
     * <p>For a caller that wants to <em>report</em> what was drawn rather than to draw. Asking
     * {@link #activeState} again after a frame would take a second transition and print a state the
     * frame did not use — the instrument disagreeing with the renderer, which this project has paid
     * for twice already.
     */
    public String currentState(Playback playback) {
        return playback.stateOf(this, initial);
    }

    public String activeState(Playback playback, MolangContext context) {
        String current = playback.stateOf(this, initial);
        State state = states.get(current);
        if (state == null) {
            return current;
        }
        for (Map.Entry<String, MolangExpr> transition : state.transitions()) {
            if (transition.getValue().evaluate(context) != 0.0f) {
                String next = transition.getKey();
                if (states.containsKey(next)) {
                    playback.enter(this, next);
                    return next;
                }
                // A transition to a state the file does not declare stays put rather than leaving
                // the machine pointing at nothing. Constitution rule 5.
                return current;
            }
        }
        return current;
    }

    /** What this and everything it plays could not compile. */
    @Override
    public Set<String> unreadableExpressions() {
        Set<String> out = new LinkedHashSet<>(unreadable);
        states.values().forEach(state -> state.plays()
                .forEach(play -> out.addAll(play.getKey().unreadableExpressions())));
        return java.util.Collections.unmodifiableSet(out);
    }

    /**
     * A pack's expression, compiled, or a zero that never throws.
     *
     * <p>Constitution rule 5, in a render pass: a guard that will not compile answers false, which
     * costs that one transition. The alternative is a client that stops drawing.
     */
    private MolangExpr compile(String source) {
        try {
            return MolangExpr.compile(source);
        } catch (RuntimeException unparsed) {
            unreadable.add(source);
            return MolangExpr.zero();
        }
    }
}
