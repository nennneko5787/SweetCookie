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
        assertEquals(Optional.of(3.0f), root.keyframes().get(0.0f).components().get(1).number());

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
                head.components().get(0).molang());
        assertEquals(Optional.of(-9.0f), head.components().get(2).number());
        assertEquals(Optional.empty(), head.components().get(2).molang());

        // A bare number means all three axes. This is how a pack hides a bone.
        AnimationIr.Keyframe hidden = idle.bones().get("bag2").scale().orElseThrow()
                .keyframes().get(0.0f);
        assertTrue(hidden.isNumeric());
        assertEquals(Optional.of(0.0f), hidden.components().get(0).number());
        assertEquals(Optional.of(0.0f), hidden.components().get(2).number());
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

    @Test
    void aKeyframeCarryingPreAndPostIsReadFromPost() {
        // A step in the animation: `pre` is the value up TO that time and `post` from it onward.
        // An interpolation starting at the keyframe needs the second.
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
        assertEquals(Optional.of(90.0f),
                animation.bones().get("a").rotation().orElseThrow()
                        .keyframes().get(1.0f).components().get(0).number());
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
