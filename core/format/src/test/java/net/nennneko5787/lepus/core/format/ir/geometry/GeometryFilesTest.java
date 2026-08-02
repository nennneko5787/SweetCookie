package net.nennneko5787.lepus.core.format.ir.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.ir.IrDiagnostics;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.Provenance;
import net.nennneko5787.lepus.core.format.value.Vec2f;
import net.nennneko5787.lepus.core.format.value.Vec3f;
import org.junit.jupiter.api.Test;

/** The two geometry families and their normalisation into one IR. SC-180 §3. */
@ProvesSpec("SC-180")
class GeometryFilesTest {

    private static final Provenance WHERE = Provenance.file(PackId.NONE, "models/entity/x.geo.json");

    private Diagnostics diagnostics = new Diagnostics();

    private List<GeometryIr> parse(String json) {
        diagnostics = new Diagnostics();
        JsonObject root = Json.parse(json).asObject().orElseThrow();
        return GeometryFiles.parse(root, WHERE, diagnostics);
    }

    private boolean reported(int code) {
        return !diagnostics.snapshot().withCode(code).isEmpty();
    }

    @Test
    @ProvesSpec("SC-180#geometry/family_1_8")
    void readsTheLegacyFamily() {
        GeometryIr model = parse("""
                {
                  "format_version": "1.8.0",
                  "geometry.wizard": {
                    "texturewidth": 64, "textureheight": 32,
                    "bones": [ { "name": "body", "pivot": [0, 12, 0] } ]
                  }
                }
                """).get(0);

        assertEquals("geometry.wizard", model.identifier());
        assertEquals(GeometryFamily.LEGACY_1_8, model.sourceFamily());
        assertEquals(64, model.textureWidth());
        assertEquals(new Vec3f(0f, 12f, 0f), model.bone("body").orElseThrow().pivot());
        assertFalse(reported(IrDiagnostics.VERSION_SNIFFED.code()));
    }

    @Test
    @ProvesSpec("SC-180#geometry/family_modern")
    void readsTheModernFamily() {
        GeometryIr model = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [
                    {
                      "description": { "identifier": "geometry.wizard",
                                       "texture_width": 64, "texture_height": 32 },
                      "bones": [ { "name": "body", "pivot": [0, 12, 0] } ]
                    }
                  ]
                }
                """).get(0);

        assertEquals("geometry.wizard", model.identifier());
        assertEquals(GeometryFamily.MODERN, model.sourceFamily());
        assertEquals(64, model.textureWidth());
    }

    @Test
    @ProvesSpec("SC-180")
    void theStructureBeatsTheDeclarationInBothDirections() {
        // The authoring tools have shipped this mismatch for years. Trusting the declaration means
        // bones that parse as empty and an entity that renders as nothing, with no error an author
        // could act on.
        GeometryIr lying = parse("""
                {
                  "format_version": "1.8.0",
                  "minecraft:geometry": [
                    { "description": { "identifier": "geometry.a" }, "bones": [] }
                  ]
                }
                """).get(0);
        assertEquals(GeometryFamily.MODERN, lying.sourceFamily());
        assertEquals("1.8.0", lying.provenance().declaredVersion());
        assertTrue(reported(IrDiagnostics.VERSION_SNIFFED.code()));

        GeometryIr alsoLying = parse("""
                {
                  "format_version": "1.21.0",
                  "geometry.a": { "bones": [] }
                }
                """).get(0);
        assertEquals(GeometryFamily.LEGACY_1_8, alsoLying.sourceFamily());
        assertTrue(reported(IrDiagnostics.VERSION_SNIFFED.code()));
    }

    @Test
    @ProvesSpec("SC-180#geometry/box_uv")
    void expandsBoxUvIntoEveryFace() {
        // The layout is asserted rather than verified - see SC-180 §3.3. What this test pins is that
        // the expansion happens at all, is complete, and uses the cube's own size.
        CubeIr cube = parse("""
                {
                  "format_version": "1.8.0",
                  "geometry.a": {
                    "bones": [ { "name": "b", "cubes": [
                      { "origin": [0, 0, 0], "size": [4, 6, 2], "uv": [10, 20] }
                    ] } ]
                  }
                }
                """).get(0).bone("b").orElseThrow().cubes().get(0);

        assertEquals(6, cube.uv().size());
        assertEquals(new Vec2f(12f, 22f), cube.face(CubeFace.NORTH).orElseThrow().uv());
        assertEquals(new Vec2f(4f, 6f), cube.face(CubeFace.NORTH).orElseThrow().uvSize());
        assertEquals(new Vec2f(10f, 22f), cube.face(CubeFace.WEST).orElseThrow().uv());
        assertEquals(new Vec2f(2f, 6f), cube.face(CubeFace.WEST).orElseThrow().uvSize());
        assertEquals(new Vec2f(12f, 20f), cube.face(CubeFace.UP).orElseThrow().uv());
        assertEquals(new Vec2f(4f, 2f), cube.face(CubeFace.UP).orElseThrow().uvSize());
    }

    @Test
    @ProvesSpec("SC-180#geometry/per_face_uv")
    void acceptsBoxUvInsideAModernFile() {
        // Box UV is not a family marker: modern files use it constantly, so the shape of the VALUE
        // decides how a cube's uv is read, not the file's family.
        CubeIr cube = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [ {
                    "description": { "identifier": "geometry.a" },
                    "bones": [ { "name": "b", "cubes": [
                      { "origin": [0, 0, 0], "size": [2, 2, 2], "uv": [0, 0] }
                    ] } ]
                  } ]
                }
                """).get(0).bone("b").orElseThrow().cubes().get(0);

        assertEquals(6, cube.uv().size());
        assertFalse(reported(IrDiagnostics.FIELD_MALFORMED.code()));
    }

    @Test
    @ProvesSpec("SC-180#geometry/per_face_uv")
    void readsPerFaceUvIncludingMaterialInstances() {
        CubeIr cube = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [ {
                    "description": { "identifier": "geometry.a" },
                    "bones": [ { "name": "b", "cubes": [ {
                      "origin": [0, 0, 0], "size": [2, 2, 2],
                      "uv": {
                        "north": { "uv": [1, 2], "uv_size": [3, 4], "material_instance": "glow" },
                        "up": { "uv": [5, 6], "uv_size": [7, 8] }
                      }
                    } ] } ]
                  } ]
                }
                """).get(0).bone("b").orElseThrow().cubes().get(0);

        // Only the faces the file declared. A missing face is missing, not guessed - a guessed
        // rectangle renders the wrong part of the texture and looks deliberate.
        assertEquals(2, cube.uv().size());
        assertEquals("glow",
                cube.face(CubeFace.NORTH).orElseThrow().materialInstance().orElseThrow());
        assertTrue(cube.face(CubeFace.SOUTH).isEmpty());
    }

    @Test
    @ProvesSpec("SC-180#geometry/inheritance")
    void splitsTheParentSyntaxOnTheFirstColon() {
        GeometryIr model = parse("""
                { "format_version": "1.8.0", "geometry.child:geometry.parent": { "bones": [] } }
                """).get(0);

        assertEquals("geometry.child", model.identifier());
        assertEquals("geometry.parent", model.parent().orElseThrow());
    }

    @Test
    @ProvesSpec("SC-180#geometry/locators")
    void normalisesBothLocatorSpellingsAndSortsThem() {
        List<LocatorIr> locators = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [ {
                    "description": { "identifier": "geometry.a" },
                    "bones": [ { "name": "b", "locators": {
                      "zed":  [1, 2, 3],
                      "abel": { "offset": [4, 5, 6], "rotation": [0, 90, 0] }
                    } } ]
                  } ]
                }
                """).get(0).bone("b").orElseThrow().locators();

        assertEquals(List.of("abel", "zed"), locators.stream().map(LocatorIr::name).toList());
        assertEquals(new Vec3f(4f, 5f, 6f), locators.get(0).offset());
        assertEquals(new Vec3f(0f, 90f, 0f), locators.get(0).rotation());
        assertEquals(new Vec3f(1f, 2f, 3f), locators.get(1).offset());
    }

    @Test
    @ProvesSpec("SC-110")
    void keepsUnrecognisedKeysAndDropsUnderscoreComments() {
        GeometryIr model = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [ {
                    "_comment": "the community's universal convention for a note to a human",
                    "description": { "identifier": "geometry.a" },
                    "bones": [],
                    "some_future_thing": { "x": 1 }
                  } ]
                }
                """).get(0);

        assertEquals(java.util.Set.of("some_future_thing"), model.unknown().names());
    }

    @Test
    @ProvesSpec("SC-110")
    void skipsWhatItCannotUseAndKeepsTheRest() {
        // SC-000 §10: a nameless bone is skipped, a malformed field is dropped, and everything else
        // in the file still parses. Losing one bone costs less than losing the model.
        GeometryIr model = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [ {
                    "description": { "identifier": "geometry.a" },
                    "bones": [
                      { "pivot": [0, 0, 0] },
                      { "name": "good", "inflate": "not a number" }
                    ]
                  } ]
                }
                """).get(0);

        assertEquals(List.of("good"), model.bones().stream().map(BoneIr::name).toList());
        assertEquals(0f, model.bone("good").orElseThrow().inflate());
        assertTrue(reported(IrDiagnostics.FIELD_REQUIRED.code()));
        assertTrue(reported(IrDiagnostics.FIELD_MALFORMED.code()));
    }

    @Test
    @ProvesSpec("SC-180")
    void defaultsAnAbsentTextureSizeRatherThanLeavingItZero() {
        // A divisor of zero puts every UV at infinity, and 1.8.0 files omit it constantly.
        GeometryIr model = parse("""
                { "format_version": "1.8.0", "geometry.a": { "bones": [] } }
                """).get(0);
        assertEquals(GeometryIr.DEFAULT_TEXTURE_SIZE, model.textureWidth());
        assertEquals(GeometryIr.DEFAULT_TEXTURE_SIZE, model.textureHeight());
    }

    @Test
    @ProvesSpec("SC-180")
    void readsEveryModelInAMultiModelFile() {
        assertEquals(2, parse("""
                {
                  "format_version": "1.8.0",
                  "geometry.a": { "bones": [] },
                  "geometry.b": { "bones": [] }
                }
                """).size());
    }
}
