package net.nennneko5787.lepus.core.format.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.ir.animation.AnimationFiles;
import net.nennneko5787.lepus.core.format.ir.animation.AnimationIr;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.Provenance;
import net.nennneko5787.lepus.core.molang.MolangContext;
import org.junit.jupiter.api.Test;

/**
 * An animation at a moment. SC-180 §4.
 *
 * <p>Every assertion is a transformed point rather than a matrix: "the bone is here at 0.5 seconds"
 * is the claim that matters, and a matrix comparison passes for the wrong reason too often.
 */
@ProvesSpec("SC-180")
class AnimationSamplerTest {

    private static final Provenance WHERE =
            Provenance.file(PackId.NONE, "animations/x.animation.json");
    private static final float EPSILON = 0.001f;

    /** No engine state: every query reads zero, which is what a static preview evaluates against. */
    private static final MolangContext NO_WORLD = MolangContext.standalone();

    private static AnimationSampler sampler(String json) {
        JsonObject root = Json.parse(json).asObject().orElseThrow();
        AnimationIr animation = AnimationFiles.parse(root, WHERE, new Diagnostics()).get(0);
        return new AnimationSampler(animation);
    }

    private static void assertPoint(float[] actual, float x, float y, float z) {
        assertEquals(x, actual[0], EPSILON, java.util.Arrays.toString(actual));
        assertEquals(y, actual[1], EPSILON, java.util.Arrays.toString(actual));
        assertEquals(z, actual[2], EPSILON, java.util.Arrays.toString(actual));
    }

    private static final String MOVING = """
            {
              "format_version": "1.8.0",
              "animations": {
                "animation.x": {
                  "loop": true,
                  "animation_length": 2,
                  "bones": {
                    "arm": { "position": { "0.0": [0, 0, 0], "2.0": [0, 10, 0] } }
                  }
                }
              }
            }""";

    @Test
    void aValueBetweenTwoKeyframesIsInterpolated() {
        // Half way between 0 and 10 at half the animation. Without interpolation the bone would
        // teleport between keyframes, which reads as the model juddering rather than as a sampler
        // that only returns keyframes.
        Map<String, Mat4f> pose = sampler(MOVING).at(1.0f, NO_WORLD);
        assertPoint(pose.get("arm").transform(0, 0, 0), 0, 5, 0);
    }

    @Test
    void aLoopingAnimationWrapsRatherThanRunningOff() {
        AnimationSampler sampler = sampler(MOVING);
        // 2.5s into a 2s loop is 0.5s in, which is a quarter of the way.
        assertPoint(sampler.at(2.5f, NO_WORLD).get("arm").transform(0, 0, 0), 0, 2.5f, 0);
        // And the loop point itself is the start again, not the end.
        assertPoint(sampler.at(2.0f, NO_WORLD).get("arm").transform(0, 0, 0), 0, 0, 0);
    }

    @Test
    void anAnimationThatDoesNotLoopHoldsItsLastFrame() {
        AnimationSampler sampler = sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": {
                      "animation_length": 2,
                      "bones": { "arm": { "position": { "0.0": [0, 0, 0], "2.0": [0, 10, 0] } } }
                    }
                  }
                }""");
        assertPoint(sampler.at(99.0f, NO_WORLD).get("arm").transform(0, 0, 0), 0, 10, 0);
    }

    @Test
    void aTimeOutsideTheKeyframesTakesTheNearestOne() {
        // Held at both ends. A channel whose first keyframe is at 1.0 has a value at 0.0 too, and
        // extrapolating instead would fling the bone away from the model before it starts.
        AnimationSampler sampler = sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": {
                      "bones": { "arm": { "position": { "1.0": [0, 4, 0], "2.0": [0, 8, 0] } } }
                    }
                  }
                }""");
        assertPoint(sampler.at(0.0f, NO_WORLD).get("arm").transform(0, 0, 0), 0, 4, 0);
        assertPoint(sampler.at(9.0f, NO_WORLD).get("arm").transform(0, 0, 0), 0, 8, 0);
    }

    @Test
    void aBoneTheAnimationDoesNotMentionIsAbsentRatherThanIdentity() {
        // The caller composes this OVER the model's bind pose. An identity written for an
        // unmentioned bone would erase whatever that bone was declared with - which is every
        // rotation in the geometry file.
        Map<String, Mat4f> pose = sampler(MOVING).at(0.0f, NO_WORLD);
        assertTrue(pose.containsKey("arm"));
        assertFalse(pose.containsKey("head"), pose.keySet().toString());
    }

    @Test
    void aMolangComponentIsEvaluatedRatherThanDropped() {
        // Real packs write these constantly, mixed with plain numbers in the same vector. Against a
        // context with no world every query reads zero, so this is -10 - which is the static pose a
        // preview shows and exactly what the corpus's head bones come to.
        AnimationSampler sampler = sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": {
                      "bones": {
                        "head": {
                          "rotation": ["query.target_x_rotation - 10 - this", 0, 0]
                        }
                      }
                    }
                  }
                }""");
        // A Bedrock rotation of -10 about X tips a point above the origin towards +Z, because
        // Bedrock's angles turn the opposite way to a right-handed turn (BoneMatrices.rotate).
        // This assertion used to expect the right-handed answer and agreed with code that was
        // wrong the same way; a character's legs swinging backwards is what exposed both.
        float[] at = sampler.at(0.0f, NO_WORLD).get("head").transform(0, 1, 0);
        assertEquals(0.0f, at[0], EPSILON);
        assertEquals(Math.cos(Math.toRadians(10)), at[1], EPSILON);
        assertEquals(Math.sin(Math.toRadians(10)), at[2], EPSILON);
    }

    @Test
    void anExpressionTheEngineCannotParseDoesNotEndTheFrame() {
        // Constitution rule 5, at its sharpest: this runs inside a render pass sixty times a
        // second, so one unparsed expression is not a diagnostic - it is a client that stops
        // drawing. The bone loses that component and keeps the rest.
        AnimationSampler sampler = sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": {
                      "bones": { "arm": { "position": ["!!! not molang !!!", 7, 0] } }
                    }
                  }
                }""");
        assertPoint(sampler.at(0.0f, NO_WORLD).get("arm").transform(0, 0, 0), 0, 7, 0);
        assertEquals(1, sampler.unreadableExpressions().size());
    }

    @Test
    void scaleZeroHidesABone() {
        // How a pack turns a bone off - `"scale": 0`, a bare number meaning all three axes. Every
        // point collapses to the bone's own origin, which draws nothing.
        AnimationSampler sampler = sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": { "bones": { "bag": { "scale": 0 } } }
                  }
                }""");
        assertPoint(sampler.at(0.0f, NO_WORLD).get("bag").transform(5, 5, 5), 0, 0, 0);
    }
}
