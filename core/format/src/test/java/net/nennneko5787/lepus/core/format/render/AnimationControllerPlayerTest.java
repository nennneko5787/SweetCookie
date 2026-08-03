package net.nennneko5787.lepus.core.format.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.ir.animation.AnimationControllerFiles;
import net.nennneko5787.lepus.core.format.ir.animation.AnimationControllerIr;
import net.nennneko5787.lepus.core.format.ir.animation.AnimationFiles;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.Provenance;
import org.junit.jupiter.api.Test;

/**
 * Which state a controller is in, and what that draws. SC-180 §5.
 *
 * <p>The fixture is the corpus's own shape: a {@code default} state with a transition per situation,
 * and each situation's state carrying the way back out. Every state is one hop from the initial one,
 * which is why a holder's first frame already lands in the right one — the machine takes a single
 * transition per frame, as Bedrock's does.
 *
 * <p>Each assertion passes a <b>fresh {@link Playback}</b>, which is a holder who has just picked
 * the item up. That is the strictest case: it is the frame with no history to lean on.
 */
@ProvesSpec("SC-180")
class AnimationControllerPlayerTest {

    private static final Provenance WHERE = Provenance.file(PackId.NONE, "x.json");
    private static final float EPSILON = 0.001f;

    /** A controller shaped like the corpus's: one state per situation, all reachable from default. */
    private static final String WEARER = """
            {
              "format_version": "1.10.0",
              "animation_controllers": {
                "controller.animation.x": {
                  "initial_state": "default",
                  "states": {
                    "default": {
                      "animations": [ "stand" ],
                      "transitions": [
                        { "sneaking": "query.is_sneaking" },
                        { "swimming": "query.is_in_water" }
                      ]
                    },
                    "sneaking": {
                      "animations": [ "sneak" ],
                      "transitions": [ { "default": "!query.is_sneaking" } ]
                    },
                    "swimming": {
                      "animations": [ "swim" ],
                      "transitions": [ { "default": "!query.is_in_water" } ]
                    }
                  }
                }
              }
            }""";

    private static AnimationControllerIr controller(String json) {
        return AnimationControllerFiles.parse(Json.parse(json).asObject().orElseThrow(),
                WHERE, new Diagnostics()).get(0);
    }

    /** One bone moved by a readable amount, so "which animation ran" is legible as a number. */
    private static AnimationSampler moves(float by) {
        return new AnimationSampler(AnimationFiles.parse(Json.parse("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.t": {"loop": true, "animation_length": 1,
                                    "bones": {"root": {"position": [%s, 0, 0]}}}
                  }
                }
                """.formatted(by)).asObject().orElseThrow(), WHERE, new Diagnostics()).get(0));
    }

    /** The three states' animations, told apart by an order of magnitude each. */
    private static Map<String, Playable> animations() {
        Map<String, Playable> out = new LinkedHashMap<>();
        out.put("stand", moves(1));
        out.put("sneak", moves(10));
        out.put("swim", moves(100));
        return out;
    }

    private static float xOf(Playable playable, AttachableContext context) {
        Map<String, AnimationSampler.Channels> channels = new LinkedHashMap<>();
        playable.accumulate(new Playback(), context, 1.0f, channels);
        AnimationSampler.Channels root = channels.get("root");
        return root == null ? 0f : root.transform().transform(0f, 0f, 0f)[0];
    }

    private static AttachableContext wearer(AttachableContext.Wearer doing) {
        return AttachableContext.thirdPerson(true).doing(doing);
    }

    @Test
    void aWearerDoingNothingStaysInTheInitialState() {
        AnimationControllerPlayer player =
                new AnimationControllerPlayer(controller(WEARER), animations());
        AttachableContext context = wearer(AttachableContext.Wearer.STANDING);
        assertEquals("default", player.activeState(new Playback(), context));
        assertEquals(1.0f, xOf(player, context), EPSILON);
    }

    /**
     * <b>Sneaking reaches the sneaking state, and this is the whole point of the feature.</b>
     *
     * <p>A pack that ships a sneaking pose drew its standing one until now, because a name that
     * resolved to a controller was skipped in silence. Nothing about the animations changed — the
     * machine that chooses between them was simply never run.
     */
    @Test
    void sneakingReachesTheSneakingState() {
        AnimationControllerPlayer player =
                new AnimationControllerPlayer(controller(WEARER), animations());
        AttachableContext context =
                wearer(new AttachableContext.Wearer(true, false, false, false, false, false));
        assertEquals("sneaking", player.activeState(new Playback(), context));
        assertEquals(10.0f, xOf(player, context), EPSILON);
    }

    /**
     * The transitions are tried in order and the first non-zero one wins.
     *
     * <p>Sneaking underwater satisfies both guards of the default state. Mojang: "the first to
     * return non-zero is the state to transition to" — so the answer is the earlier entry, and a
     * reader that kept the transitions in a map would have a fifty-fifty chance of this.
     */
    @Test
    void theFirstTransitionThatFiresWins() {
        AnimationControllerPlayer player =
                new AnimationControllerPlayer(controller(WEARER), animations());
        assertEquals("sneaking", player.activeState(new Playback(),
                wearer(new AttachableContext.Wearer(true, true, false, false, false, false))));
    }

    /**
     * A cycle in the pack's own file flickers, one hop a frame, rather than spinning inside one.
     *
     * <p>The corpus has one: a {@code walking} state whose way back is {@code !query.is_gliding},
     * true whenever the player is not gliding, so {@code default → walking → default} is a loop the
     * pack wrote. Taking a single transition per frame is what makes that harmless, and it is also
     * what Bedrock does — the character flickers between two states that both draw nothing.
     */
    @Test
    void aCycleInTheControllerTakesOneHopPerFrame() {
        AnimationControllerPlayer player = new AnimationControllerPlayer(controller("""
                {
                  "format_version": "1.10.0",
                  "animation_controllers": {
                    "controller.animation.x": {
                      "states": {
                        "default": { "transitions": [ { "walking": 1 } ] },
                        "walking": { "animations": [ "sneak" ],
                                     "transitions": [ { "default": 1 } ] }
                      }
                    }
                  }
                }"""), animations());
        Playback playback = new Playback();
        AttachableContext standing = wearer(AttachableContext.Wearer.STANDING);
        assertEquals("walking", player.activeState(playback, standing));
        // And back, on the next frame, because the machine REMEMBERS where it was. A resolver that
        // ran from the initial state every frame would answer "walking" forever and never show the
        // flicker the Bedrock client has.
        assertEquals("default", player.activeState(playback, standing));
    }

    /**
     * A state naming an animation the attachable does not define plays nothing.
     *
     * <p><b>Not an error, and this is the common case.</b> Mojang's own elytra controller asks for
     * {@code default}, {@code gliding}, {@code sneaking}, {@code sleeping} and {@code swimming}; the
     * attachables that borrow it define two of the five. The other three are meant to draw nothing.
     */
    @Test
    void aStateNamingAnUndefinedAnimationDrawsNothing() {
        Map<String, Playable> partial = new LinkedHashMap<>();
        partial.put("sneak", moves(10));
        AnimationControllerPlayer player =
                new AnimationControllerPlayer(controller(WEARER), partial);
        // `stand` is undefined, so the default state contributes nothing at all - not an identity
        // for the bone, which would erase what the model was declared with.
        assertEquals(0.0f, xOf(player, wearer(AttachableContext.Wearer.STANDING)), EPSILON);
    }

    /** A state's blend expression multiplies the blend the entry playing the controller carries. */
    @Test
    void aStatesBlendMultipliesTheEntrysOwn() {
        AnimationControllerPlayer player = new AnimationControllerPlayer(controller("""
                {
                  "format_version": "1.10.0",
                  "animation_controllers": {
                    "controller.animation.x": {
                      "states": { "default": { "animations": [ { "swim": "0.5" } ] } }
                    }
                  }
                }"""), animations());
        Map<String, AnimationSampler.Channels> channels = new LinkedHashMap<>();
        player.accumulate(new Playback(), wearer(AttachableContext.Wearer.STANDING), 0.5f, channels);
        assertEquals(25.0f, channels.get("root").transform().transform(0f, 0f, 0f)[0], EPSILON);
    }
}
