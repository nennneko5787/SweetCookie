package net.nennneko5787.lepus.core.format.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.ir.animation.AnimationFiles;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryFiles;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.Provenance;
import org.junit.jupiter.api.Test;

/**
 * Which animation owns a bone when several name it. SC-180 §4.1.
 *
 * <p><b>The last one takes it.</b> Three rules have been in this file — matrix composition, value
 * addition, and this — and each was put here to explain a screenshot. Only this one survives every
 * screenshot taken so far. SC-180 §4.1 records what the other two looked like when they were wrong,
 * because "it fixed the thing I was looking at" is what all three had in common.
 *
 * <p>Asserted as a transformed point rather than as a matrix: "the cube is here" is the claim, and
 * a matrix comparison passes for the wrong reason too often.
 */
@ProvesSpec("SC-180")
class AttachablePoserTest {

    private static final Provenance WHERE = Provenance.file(PackId.NONE, "x.json");
    private static final float EPSILON = 0.001f;

    /** One bone with one cube at the origin, so a transform is readable straight off the point. */
    private static GeometryIr geometry() {
        return GeometryFiles.parse(Json.parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {"identifier": "geometry.t", "texture_width": 16,
                                    "texture_height": 16},
                    "bones": [{"name": "root", "pivot": [0, 0, 0],
                               "cubes": [{"origin": [0, 0, 0], "size": [1, 1, 1]}]}]
                  }]
                }
                """).asObject().orElseThrow(), WHERE, new Diagnostics()).get(0);
    }

    private static AnimationSampler moving(String bone, String position) {
        JsonObject root = Json.parse("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.t": {"loop": true, "animation_length": 1,
                                    "bones": {"%s": {"position": %s}}}
                  }
                }
                """.formatted(bone, position)).asObject().orElseThrow();
        return new AnimationSampler(AnimationFiles.parse(root, WHERE, new Diagnostics()).get(0));
    }

    // The length is not decoration. A looping animation with no keyframes has a length of zero,
    // which means it ends on the frame it starts — and SC-180 §4.1 has this build drop such an
    // animation, because that is the one difference between the corpus's two first-person poses.
    // Without a length these fixtures test that rule instead of the composition they are about.

    private static float[] at(AttachablePoser poser) {
        Map<String, Mat4f> pose = poser.at(0f, AttachableContext.thirdPerson(true));
        return pose.get("root").transform(0f, 0f, 0f);
    }

    /**
     * Two animations naming one bone <b>add, per channel component</b>. SC-180 §4.1.
     *
     * <p>Ten and one hundred rather than two numbers that could be confused: adding reads 110, the
     * last one winning reads 100, the first one winning reads 10. All three have been shipped, so
     * each gets a number that names it.
     *
     * <p><b>This assertion used to read 100, and that is the point of keeping the numbers apart.</b>
     * Bedrock's documentation says the skeleton is reset to the bind pose each frame and animations
     * are "applied per-channel-additively in order", with the components summed across animations
     * before any transform is built. The build asserted last-one-wins for a while — a rule with no
     * source behind it, adopted because it explained a screenshot — and this test agreed with it.
     * A test that agrees with the code's mistake is the failure mode this file exists to avoid.
     */
    @Test
    void twoAnimationsNamingOneBoneAdd() {
        AttachablePoser poser = new AttachablePoser(geometry(),
                List.of(Map.entry(moving("root", "[10, 0, 0]"), Optional.<String>empty()),
                        Map.entry(moving("root", "[100, 0, 0]"), Optional.<String>empty())),
                List.of());
        assertEquals(110.0f, at(poser)[0], EPSILON);
    }

    /** A bone only one animation names gets exactly that one, with nothing added to it. */
    @Test
    void aBoneOnlyOneAnimationNamesKeepsThatOne() {
        AttachablePoser poser = new AttachablePoser(geometry(),
                List.of(Map.entry(moving("root", "[10, 0, 0]"), Optional.<String>empty()),
                        Map.entry(moving("other", "[100, 0, 0]"), Optional.<String>empty())),
                List.of());
        assertEquals(10.0f, at(poser)[0], EPSILON);
    }

    /**
     * A conditional entry that reads false is not in the list at all, so it cannot claim a bone.
     *
     * <p>Which is what makes the two views differ: {@code c.is_first_person} decides whether the
     * animation that would take the root bone gets to run.
     */
    @Test
    void anEntryWhoseConditionIsFalseClaimsNothing() {
        AttachablePoser poser = new AttachablePoser(geometry(),
                List.of(Map.entry(moving("root", "[10, 0, 0]"), Optional.<String>empty()),
                        Map.entry(moving("root", "[100, 0, 0]"),
                                Optional.of("c.is_first_person"))),
                List.of());
        assertEquals(10.0f, at(poser)[0], EPSILON);
    }
}
