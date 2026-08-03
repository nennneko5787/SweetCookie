package net.nennneko5787.lepus.core.format.ir.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/** What each bone does over time. SC-180 §4. */
@ProvesSpec("SC-180")
class AnimationFilesTest {

    private static final Provenance WHERE =
            Provenance.file(PackId.NONE, "animations/x.animation.json");

    private static List<AnimationIr> parse(String json) {
        JsonObject root = Json.parse(json).asObject().orElseThrow();
        return AnimationFiles.parse(root, WHERE, new Diagnostics());
    }

    /**
     * One real animation, with all four shapes a channel is written in.
     *
     * <p>Copied from an installed add-on. All four appear in ONE file there, and a reader that knew
     * three of them would drop the fourth in silence — a bone that never moves is not an error
     * anywhere, which is what makes this worth pinning rather than trusting.
     */
    @Test
    void readsEveryShapeAChannelIsWrittenIn() {
        AnimationIr idle = parse("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.shiroko_onbu.idle": {
                      "loop": true,
                      "animation_length": 2,
                      "bones": {
                        "root3": { "position": [0, 3, 0] },
                        "body3": {
                          "rotation": { "0.0": [0, 0, 0], "1.0": [2, 0, 0], "2.0": [0, 0, 0] }
                        },
                        "head2": {
                          "rotation": ["query.target_x_rotation - 10 - this", 0, -9]
                        },
                        "bag2": { "scale": 0 }
                      }
                    }
                  }
                }""").get(0);

        assertEquals("animation.shiroko_onbu.idle", idle.name());
        assertTrue(idle.loop());
        assertEquals(Optional.of(2.0f), idle.length());

        // A constant is a timeline of one, so the sampler has one shape to handle rather than two.
        AnimationIr.Channel root = idle.bones().get("root3").position().orElseThrow();
        assertTrue(root.isConstant());
        assertEquals(Optional.of(3.0f), root.keyframes().get(0.0f).post().get(1).number());

        // Keyframes, in time order.
        AnimationIr.Channel body = idle.bones().get("body3").rotation().orElseThrow();
        assertEquals(List.of(0.0f, 1.0f, 2.0f), List.copyOf(body.keyframes().keySet()));
        assertFalse(body.isConstant());

        // Molang kept as SOURCE, never as something evaluable (SC-110 §7). These reach for the
        // world - the player's head angle - and cannot be folded to a number here.
        //
        // Per COMPONENT, not per vector: real packs mix an expression with plain numbers inside one
        // rotation, and a reader that decided "expression" or "numbers" for the whole three would
        // have to lose one or the other.
        AnimationIr.Keyframe head = idle.bones().get("head2").rotation().orElseThrow()
                .keyframes().get(0.0f);
        assertFalse(head.isNumeric());
        assertEquals(Optional.of("query.target_x_rotation - 10 - this"),
                head.post().get(0).molang());
        assertEquals(Optional.of(-9.0f), head.post().get(2).number());
        assertEquals(Optional.empty(), head.post().get(2).molang());

        // A bare number means all three axes. This is how a pack hides a bone.
        AnimationIr.Keyframe hidden = idle.bones().get("bag2").scale().orElseThrow()
                .keyframes().get(0.0f);
        assertTrue(hidden.isNumeric());
        assertEquals(Optional.of(0.0f), hidden.post().get(0).number());
        assertEquals(Optional.of(0.0f), hidden.post().get(2).number());
    }

    /**
     * Keyframe times are numbers, not text.
     *
     * <p>Sorting them as strings puts {@code "10.0"} before {@code "2.0"} and plays the animation
     * out of order — which on screen looks like the model juddering, not like a parse bug.
     */
    @Test
    void keyframeTimesSortAsNumbers() {
        AnimationIr animation = parse("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": {
                      "bones": {
                        "a": { "position": { "10.0": [1, 0, 0], "2.0": [2, 0, 0] } }
                      }
                    }
                  }
                }""").get(0);
        assertEquals(List.of(2.0f, 10.0f),
                List.copyOf(animation.bones().get("a").position().orElseThrow()
                        .keyframes().keySet()));
    }

    /**
     * A keyframe carrying {@code pre} and {@code post} keeps <b>both</b>. SC-180 §4.1.2.
     *
     * <p>They are the value the channel arrives at and the value it leaves with, which is how an
     * animation steps at an instant. This reader took `post` and dropped `pre` for as long as it
     * has existed, turning every step into a ramp on the incoming edge — invisible, because no
     * animation in the surveyed corpus writes one.
     */
    @Test
    void aKeyframeCarryingPreAndPostKeepsBoth() {
        AnimationIr animation = parse("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": {
                      "bones": {
                        "a": { "rotation": { "1.0": { "pre": [0, 0, 0], "post": [90, 0, 0] } } }
                      }
                    }
                  }
                }""").get(0);
        AnimationIr.Keyframe step = animation.bones().get("a").rotation().orElseThrow()
                .keyframes().get(1.0f);
        assertEquals(Optional.of(0.0f), step.pre().get(0).number());
        assertEquals(Optional.of(90.0f), step.post().get(0).number());
        assertTrue(step.steps());
    }

    /**
     * {@code lerp_mode} sits on the keyframe, and one value stands for both sides. SC-180 §4.1.2.
     *
     * <p>Written exactly as the corpus has it — 233 keyframes across 22 files, every one of them
     * a bare {@code post} beside a {@code catmullrom} — so this fixture is the real shape rather
     * than the schema's most general one.
     */
    @Test
    void lerpModeIsReadFromTheKeyframeAndDefaultsToLinear() {
        AnimationIr animation = parse("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": {
                      "bones": {
                        "rightLeg": {
                          "rotation": {
                            "0.0": { "post": [-40, 0, 0], "lerp_mode": "catmullrom" },
                            "0.4167": [40, 0, 0]
                          }
                        }
                      }
                    }
                  }
                }""").get(0);
        AnimationIr.Channel leg = animation.bones().get("rightLeg").rotation().orElseThrow();
        assertEquals(AnimationIr.LerpMode.CATMULLROM, leg.keyframes().get(0.0f).lerp());
        assertEquals(Optional.of(-40.0f), leg.keyframes().get(0.0f).pre().get(0).number());
        assertFalse(leg.keyframes().get(0.0f).steps());
        assertEquals(AnimationIr.LerpMode.LINEAR, leg.keyframes().get(0.4167f).lerp());
    }

    /**
     * {@code blend_weight} is a number or an expression, and absent is not zero. SC-180 §4.1.1.
     *
     * <p>Mojang: "default = '1.0'. How much this animation is blended with the others. 0.0 = off.
     * 1.0 = fully apply all transforms. <b>Can be an expression.</b>" So it reads into the same
     * number-or-Molang shape a keyframe component has, and stays absent when unwritten — a default
     * filled in here would let nothing downstream tell "the pack said 1.0" from "the pack said
     * nothing", which is the distinction {@code override_previous_animation} will need.
     */
    @Test
    void blendWeightIsANumberOrAnExpressionAndAbsentWhenUnwritten() {
        List<AnimationIr> animations = parse("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.half":  { "blend_weight": 0.5, "bones": {} },
                    "animation.speed": { "blend_weight": "query.modified_move_speed",
                                         "bones": {} },
                    "animation.plain": { "bones": {} }
                  }
                }""");
        assertEquals(Optional.of(0.5f), animations.get(0).blendWeight().orElseThrow().number());
        assertEquals(Optional.of("query.modified_move_speed"),
                animations.get(1).blendWeight().orElseThrow().molang());
        assertEquals(Optional.empty(), animations.get(2).blendWeight());
    }

    @Test
    void aBoneWithNoChannelsIsStillABone() {
        // Legal and common: an animation may name a bone and set nothing on it. Refusing the file
        // over one would cost every other bone in it. Constitution rule 5.
        AnimationIr animation = parse("""
                {
                  "format_version": "1.8.0",
                  "animations": { "animation.x": { "bones": { "a": {} } } }
                }""").get(0);
        AnimationIr.Bone bone = animation.bones().get("a");
        assertEquals(Optional.empty(), bone.rotation());
        assertEquals(Optional.empty(), bone.position());
        assertEquals(Optional.empty(), bone.scale());
    }
}
