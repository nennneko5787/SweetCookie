package net.nennneko5787.lepus.core.format.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryFiles;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.Provenance;
import org.junit.jupiter.api.Test;

/**
 * Where each bone ends up. SC-180 §3.
 *
 * <p>Every assertion here is a point, not a matrix. A matrix comparison passes for the wrong reason
 * often enough to be worth avoiding, and "the elbow ends up here" is the claim that matters.
 */
@ProvesSpec("SC-180")
class BoneMatricesTest {

    private static final Provenance WHERE = Provenance.file(PackId.NONE, "models/x.geo.json");
    private static final float EPSILON = 0.001f;

    private static GeometryIr parse(String json) {
        JsonObject root = Json.parse(json).asObject().orElseThrow();
        return GeometryFiles.parse(root, WHERE, new Diagnostics()).get(0);
    }

    private static void assertPoint(float[] actual, float x, float y, float z) {
        assertEquals(x, actual[0], EPSILON, "x of " + java.util.Arrays.toString(actual));
        assertEquals(y, actual[1], EPSILON, "y of " + java.util.Arrays.toString(actual));
        assertEquals(z, actual[2], EPSILON, "z of " + java.util.Arrays.toString(actual));
    }

    /**
     * A bone with no rotation moves nothing at all.
     *
     * <p>The pivot is NOT a translation, and this is the test that says so. Treating it as one is
     * the most common way to get a bone hierarchy wrong: every cube collapses onto its bone's pivot,
     * which looks like a model that exploded rather than like a transform bug.
     */
    @Test
    void anUnrotatedBoneLeavesItsCubesWhereThePackPutThem() {
        GeometryIr model = parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc" },
                    "bones": [{ "name": "body", "pivot": [0, 24, 0] }]
                  }]
                }""");
        Map<String, Mat4f> pose = BoneMatrices.bindPose(model);
        assertPoint(pose.get("body").transform(3, 20, 1), 3, 20, 1);
    }

    /**
     * A rotation happens about the bone's own pivot.
     *
     * <p>A quarter turn about Y at pivot {@code [0, 24, 0]} sends a point one unit in front of the
     * pivot to one unit to its side, and leaves the pivot itself alone. About the ORIGIN instead,
     * the same point would land 24 units away — the difference between an arm swinging at the
     * shoulder and an arm orbiting the model's feet.
     */
    @Test
    void aBoneTurnsAboutItsPivotAndNotTheOrigin() {
        GeometryIr model = parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc" },
                    "bones": [{ "name": "arm", "pivot": [0, 24, 0], "rotation": [0, 90, 0] }]
                  }]
                }""");
        Mat4f arm = BoneMatrices.bindPose(model).get("arm");
        assertPoint(arm.transform(0, 24, 0), 0, 24, 0);
        // Bedrock's +90 about Y sends -Z to -X: a right-handed turn, UNLIKE its X and Z.
        //
        // This assertion has been both signs, and both times it agreed with the code rather than
        // checking it. It said -1 while the bone path ignored Bedrock's angle sense entirely; it was
        // changed to +1 alongside the fix for legs that swung backwards, which was an X-axis
        // failure, on the assumption that a sign convention is uniform across axes. It is not, and
        // SC-180 section 3.4.1 says why: the flip that makes X and Z turn backwards is about Y, and
        // leaves rotations about Y alone.
        //
        // What caught it was a pair of legs at +-24 degrees about Y crossing instead of spreading.
        assertPoint(arm.transform(0, 24, -1), -1, 24, 0);
    }

    /**
     * A child inherits its parent's turn, composed in the right order.
     *
     * <p>THE regression to guard. Multiplying the chain the other way round gives a model whose
     * limbs orbit the world instead of the body, and a bind pose — where most bones do not rotate
     * at all — hides it completely. The corpus this was written against orients whole characters
     * through a rotated root with the cubes several bones below it.
     */
    @Test
    void aChildInheritsItsParentsTurn() {
        GeometryIr model = parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc" },
                    "bones": [
                      { "name": "root", "pivot": [0, 0, 0], "rotation": [0, 180, 0] },
                      { "name": "head", "parent": "root", "pivot": [0, 24, 0] }
                    ]
                  }]
                }""");
        Mat4f head = BoneMatrices.bindPose(model).get("head");
        // The root turns everything half way about the model's centre line, so a point in front of
        // the head comes out behind it. Unrotated, this would still be at z = -4.
        assertPoint(head.transform(0, 24, -4), 0, 24, 4);
        // And a point that is off-centre moves on BOTH axes, which a single-axis test cannot see.
        assertPoint(head.transform(2, 24, -4), -2, 24, 4);
    }

    /**
     * Two turns in a chain compose rather than replacing each other.
     *
     * <p>A parent turned a quarter and a child turned a quarter the same way is a half turn on the
     * child's cubes. If the child's own rotation replaced the chain instead of composing with it,
     * this reads as a quarter — which looks like "the parent is being ignored", the exact symptom
     * the block transpile had before it learned to walk parent chains.
     */
    @Test
    void turnsComposeAlongTheChain() {
        GeometryIr model = parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc" },
                    "bones": [
                      { "name": "root", "pivot": [0, 0, 0], "rotation": [0, 90, 0] },
                      { "name": "arm", "parent": "root", "pivot": [0, 0, 0], "rotation": [0, 90, 0] }
                    ]
                  }]
                }""");
        assertPoint(BoneMatrices.bindPose(model).get("arm").transform(0, 0, -4), 0, 0, 4);
    }

    @Test
    void anExtraTransformAppliesInsideTheBonesOwnPivot() {
        // What animation hangs off. A quarter turn handed in for `arm` must swing it about the
        // elbow, exactly as its own rotation would - not about the model's origin.
        GeometryIr model = parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc" },
                    "bones": [{ "name": "arm", "pivot": [0, 24, 0] }]
                  }]
                }""");
        Map<String, Mat4f> pose = BoneMatrices.posed(model,
                bone -> Optional.of(Mat4f.rotationY(90)));
        assertPoint(pose.get("arm").transform(0, 24, 0), 0, 24, 0);
        assertPoint(pose.get("arm").transform(0, 24, -1), -1, 24, 0);
    }

    @Test
    void aParentChainThatCyclesIsRefusedRatherThanFollowed() {
        // Following one inside a resource reload hangs the client. Refusing the model costs the
        // model. Constitution rule 5 puts a wrong shape above a hung game.
        GeometryIr model = parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc" },
                    "bones": [
                      { "name": "a", "parent": "b", "pivot": [0, 0, 0] },
                      { "name": "b", "parent": "a", "pivot": [0, 0, 0] }
                    ]
                  }]
                }""");
        assertTrue(BoneMatrices.bindPose(model).isEmpty());
    }

    @Test
    void aParentNobodyDeclaresEndsTheChainRatherThanTheModel() {
        // Bedrock's own files do this, and SC-180 §3.2 already says the hierarchy may be
        // incomplete. The bone keeps its own transform and simply inherits nothing.
        GeometryIr model = parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc" },
                    "bones": [{ "name": "hat", "parent": "nobody", "pivot": [0, 24, 0] }]
                  }]
                }""");
        assertPoint(BoneMatrices.bindPose(model).get("hat").transform(1, 2, 3), 1, 2, 3);
    }
}
