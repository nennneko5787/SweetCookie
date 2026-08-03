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
import net.nennneko5787.lepus.core.molang.MolangContext;
import org.junit.jupiter.api.Test;

/**
 * What several animations naming one bone do to it, and how much of each applies. SC-180 §4.1.
 *
 * <p><b>They add, per channel component, each by its blend.</b> Four rules have been in this file —
 * matrix composition, last-one-wins, value addition, and addition weighted by the blend the pack
 * asked for. Three were put here to explain a screenshot and only the last two were read out of
 * Mojang's documentation. SC-180 §4.1 records what each of the others looked like when it was wrong,
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
        return sampler(bone, "position", position);
    }

    private static AnimationSampler scaling(String bone, String scale) {
        return sampler(bone, "scale", scale);
    }

    private static AnimationSampler sampler(String bone, String channel, String value) {
        JsonObject root = Json.parse("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.t": {"loop": true, "animation_length": 1,
                                    "bones": {"%s": {"%s": %s}}}
                  }
                }
                """.formatted(bone, channel, value)).asObject().orElseThrow();
        return new AnimationSampler(AnimationFiles.parse(root, WHERE, new Diagnostics()).get(0));
    }

    // The length is not decoration. A looping animation with no keyframes has a length of zero,
    // which means it ends on the frame it starts — and SC-180 §4.1 has this build drop such an
    // animation, because that is the one difference between the corpus's two first-person poses.
    // Without a length these fixtures test that rule instead of the composition they are about.

    private static float[] at(AttachablePoser poser) {
        Map<String, Mat4f> pose = poser.at(new Playback(), AttachableContext.thirdPerson(true));
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
     * <b>An animation's clock starts when its blend first becomes non-zero.</b> SC-180 §4.1.1.
     *
     * <p>Mojang: "the animation will start playing once [the query] is true/1, but it will never
     * stop playing … <b>It won't play from the start again.</b>" So an entry that has never played
     * has no time at all, and one that has plays from where its own clock has reached — not from
     * where a clock shared by the whole client has.
     *
     * <p>Asserted on a one-second loop from 0 to 10 held out for two seconds: a shared clock reads
     * the animation at t=2, which for a one-second loop is the start again by coincidence, so this
     * uses <b>2.5</b> seconds and the two answers are 5 and 0. The entry that has just started must
     * read 0.
     */
    @Test
    void anEntrysClockStartsTheFirstFrameItPlays() {
        AttachablePoser poser = new AttachablePoser(geometry(),
                List.of(Map.entry(sampler("root", "position",
                        "{\"0.0\": [0, 0, 0], \"1.0\": [10, 0, 0]}"), Optional.of("v.on"))),
                List.of());
        Playback playback = new Playback();
        // Two and a half seconds in which it never played, so its clock never started.
        playback.advanceTo(2.5f);
        AttachableContext off = AttachableContext.thirdPerson(true);
        assertEquals(0.0f, poser.at(playback, off).get("root").transform(0f, 0f, 0f)[0], EPSILON);

        // Now it plays. Its first frame is its t=0, NOT the shared clock's 2.5.
        AttachableContext on = AttachableContext.thirdPerson(true);
        on.write(MolangContext.Scope.VARIABLE, "on", 1.0f);
        assertEquals(0.0f, poser.at(playback, on).get("root").transform(0f, 0f, 0f)[0], EPSILON);

        // Half a second later it is halfway, and the shared clock's three seconds are irrelevant.
        playback.advanceTo(3.0f);
        assertEquals(5.0f, poser.at(playback, on).get("root").transform(0f, 0f, 0f)[0], EPSILON);
    }

    /**
     * A conditional entry whose expression answers zero contributes nothing.
     *
     * <p>Which is what makes the two views differ: {@code c.is_first_person} answers one in first
     * person and zero in third, and zero is a blend of none.
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

    /**
     * <b>A condition is how much, not whether.</b> SC-180 §4.1.1.
     *
     * <p>Mojang: "the query in the scripts section is only a blend value for the animation. It
     * defines 'how much' the animation plays, not when it plays and when it doesn't." Half of a
     * hundred is fifty, and the ten beneath it is untouched — so this reads 60, where a switch
     * reads 110 and no entry at all reads 10. Three numbers apart, for the same reason the two
     * above are.
     *
     * <p>Nothing in the surveyed corpus writes a fraction here: every condition in it is a
     * comparison, which answers zero or one either way. This is asserted for vanilla's own entries,
     * which blend a walk cycle by {@code query.modified_move_speed}, and because a rule that only
     * happens to agree on the inputs at hand is the kind that is found wrong by the next pack.
     */
    @Test
    void aFractionalConditionAppliesThatMuchOfIt() {
        AttachablePoser poser = new AttachablePoser(geometry(),
                List.of(Map.entry(moving("root", "[10, 0, 0]"), Optional.<String>empty()),
                        Map.entry(moving("root", "[100, 0, 0]"), Optional.of("0.5"))),
                List.of());
        assertEquals(60.0f, at(poser)[0], EPSILON);
    }

    /**
     * The animation's own {@code blend_weight} multiplies the entry's. SC-180 §4.1.1.
     *
     * <p>Two packs' worth of the same quantity: the animation says how strongly it applies, the
     * entry that plays it says how much of it to play. A quarter of a hundred is twenty-five, on
     * top of the untouched ten.
     */
    @Test
    void anAnimationsOwnBlendWeightMultipliesTheEntrys() {
        AnimationSampler half = new AnimationSampler(AnimationFiles.parse(Json.parse("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.t": {"loop": true, "animation_length": 1, "blend_weight": 0.5,
                                    "bones": {"root": {"position": [100, 0, 0]}}}
                  }
                }
                """).asObject().orElseThrow(), WHERE, new Diagnostics()).get(0));
        AttachablePoser poser = new AttachablePoser(geometry(),
                List.of(Map.entry(moving("root", "[10, 0, 0]"), Optional.<String>empty()),
                        Map.entry(half, Optional.of("0.5"))),
                List.of());
        assertEquals(35.0f, at(poser)[0], EPSILON);
    }

    /**
     * A blended scale fades towards <b>one</b>, not towards zero.
     *
     * <p>Off has to leave the bone the size it was. The additive line would make a half-blended
     * {@code scale 3} into a factor of 1.5 by multiplying the value, and Bedrock's own wording —
     * "0.0 = off. 1.0 = fully apply all transforms" — says off is a bone nothing has scaled. So the
     * factor runs from one: half of a threefold scale is twofold, and a point one unit out lands at
     * two.
     */
    @Test
    void aBlendedScaleFadesTowardsOne() {
        AttachablePoser poser = new AttachablePoser(geometry(),
                List.of(Map.entry(scaling("root", "[3, 3, 3]"), Optional.of("0.5"))),
                List.of());
        Map<String, Mat4f> pose = poser.at(new Playback(), AttachableContext.thirdPerson(true));
        assertEquals(2.0f, pose.get("root").transform(1f, 0f, 0f)[0], EPSILON);
    }
}
