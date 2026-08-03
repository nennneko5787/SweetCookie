package net.nennneko5787.lepus.core.format.ir.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.Provenance;
import org.junit.jupiter.api.Test;

/** The state machine that decides which animation plays. SC-180 §5. */
@ProvesSpec("SC-180")
class AnimationControllerFilesTest {

    private static final Provenance WHERE =
            Provenance.file(PackId.NONE, "animation_controllers/x.json");

    private static List<AnimationControllerIr> parse(String json) {
        JsonObject root = Json.parse(json).asObject().orElseThrow();
        return AnimationControllerFiles.parse(root, WHERE, new Diagnostics());
    }

    /**
     * One real controller, in the shape the corpus ships it.
     *
     * <p>Trimmed from the add-on's own file rather than from the schema: bare animation names, an
     * ordered transition list, and a {@code blend_transition} of zero — which is a value, not an
     * absence, and a reader that treated the two alike would have nothing to say about it later.
     */
    @Test
    void readsAStateMachineWithOrderedTransitions() {
        AnimationControllerIr controller = parse("""
                {
                  "format_version": "1.10.0",
                  "animation_controllers": {
                    "controller.animation.hoshino_totem.default": {
                      "initial_state": "default",
                      "states": {
                        "default": {
                          "animations": [ "default" ],
                          "transitions": [
                            { "sneaking": "query.is_sneaking && !query.is_onfire" },
                            { "swimming": "query.is_in_water" }
                          ],
                          "blend_transition": 0.0
                        },
                        "sneaking": {
                          "animations": [ "sneaking" ],
                          "transitions": [
                            { "default": "!query.is_sneaking && !query.is_onfire" }
                          ]
                        }
                      }
                    }
                  }
                }""").get(0);

        assertEquals("controller.animation.hoshino_totem.default", controller.name());
        assertEquals("default", controller.initialState());
        assertEquals(List.of("default", "sneaking"), List.copyOf(controller.states().keySet()));

        AnimationControllerIr.State first = controller.states().get("default");
        assertEquals(List.of("default"), first.animations().stream()
                .map(AnimationControllerIr.Play::name).toList());
        assertEquals(Optional.empty(), first.animations().get(0).blend());
        assertEquals(Optional.of(0.0f), first.blendTransition());

        // ORDER IS PRIORITY. Mojang: "the first to return non-zero is the state to transition to",
        // so a map keyed by target state would lose the only thing that decides between two guards
        // that are both true.
        assertEquals(List.of("sneaking", "swimming"), first.transitions().stream()
                .map(AnimationControllerIr.Transition::to).toList());
        assertEquals("query.is_sneaking && !query.is_onfire",
                first.transitions().get(0).condition());
    }

    /**
     * A state's animation entry may carry a blend, exactly as {@code scripts.animate} does.
     *
     * <p>Same shape, same meaning (SC-180 §4.1.1) — this is where Mojang's documentation puts the
     * example, blending a walk cycle by how fast the entity is moving.
     */
    @Test
    void aStateMayBlendItsAnimations() {
        AnimationControllerIr controller = parse("""
                {
                  "format_version": "1.10.0",
                  "animation_controllers": {
                    "controller.animation.x": {
                      "states": {
                        "default": {
                          "animations": [ "idle", { "walk": "query.modified_move_speed" } ]
                        }
                      }
                    }
                  }
                }""").get(0);
        AnimationControllerIr.State state = controller.states().get("default");
        assertEquals(Optional.empty(), state.animations().get(0).blend());
        assertEquals(Optional.of("query.modified_move_speed"), state.animations().get(1).blend());
        // No `initial_state`: Bedrock's default is a state called `default`, which this has.
        assertEquals("default", controller.initialState());
        assertTrue(controller.initial().isPresent());
    }

    /**
     * A guard written as a number is still a guard.
     *
     * <p>{@code {"gliding": 1}} and {@code {"gliding": "1"}} mean the same thing to Bedrock. Taking
     * only strings would drop a transition that is always true, which on screen is a state the
     * entity can never leave — and would look like a broken pose rather than a dropped guard.
     */
    @Test
    void aTransitionGuardMayBeANumber() {
        AnimationControllerIr controller = parse("""
                {
                  "format_version": "1.10.0",
                  "animation_controllers": {
                    "controller.animation.x": {
                      "states": {
                        "default": { "transitions": [ { "next": 1 } ] },
                        "next": {}
                      }
                    }
                  }
                }""").get(0);
        assertEquals("1.0", controller.states().get("default").transitions().get(0).condition());
    }
}
