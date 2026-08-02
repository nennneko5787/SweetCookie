package net.nennneko5787.lepus.core.format.ir.attachable;

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

/** The 3D model an item is held as. SC-170 §5. */
@ProvesSpec("SC-170")
class AttachableFilesTest {

    private static final Provenance WHERE =
            Provenance.file(PackId.NONE, "attachables/x.json");

    private Diagnostics diagnostics = new Diagnostics();

    private List<AttachableIr> parse(String json) {
        diagnostics = new Diagnostics();
        JsonObject root = Json.parse(json).asObject().orElseThrow();
        return AttachableFiles.parse(root, WHERE, diagnostics);
    }

    /**
     * A real attachable, field for field.
     *
     * <p>Copied from an installed add-on rather than invented. Two things in it are the ones an
     * implementation written from documentation gets wrong: the geometry map is under
     * {@code geometry} SINGULAR, and {@code scripts.animate} mixes bare names with conditional
     * objects in one array.
     */
    @Test
    void readsARealAttachable() {
        AttachableIr onbu = parse("""
                {
                  "format_version": "1.16.100",
                  "minecraft:attachable": {
                    "description": {
                      "identifier": "kivotos:shiroko_onbu",
                      "materials": { "default": "enderman" },
                      "textures": { "default": "textures/abydos/sunaookami_shiroko" },
                      "geometry": { "default": "geometry.shiroko_onbu" },
                      "animations": {
                        "default_controller": "controller.animation.elytra.default",
                        "hoshino": "animation.shiroko_onbu.idle",
                        "main_hand": "animation.shiroko_onbu.hand"
                      },
                      "scripts": {
                        "animate": [
                          { "main_hand": "v.main_hand && c.is_first_person" },
                          "hoshino",
                          "default_controller"
                        ],
                        "pre_animation": [ "v.main_hand = c.item_slot == 'main_hand';" ]
                      },
                      "render_controllers": [ "controller.render.blue_archive" ]
                    }
                  }
                }""").get(0);

        assertEquals("kivotos:shiroko_onbu", onbu.identifier().toString());
        // SINGULAR `geometry`, holding a map. The plural is the obvious guess and finds nothing.
        assertEquals(Optional.of("geometry.shiroko_onbu"), onbu.defaultGeometry());
        assertEquals(Optional.of("textures/abydos/sunaookami_shiroko"), onbu.defaultTexture());
        assertEquals("enderman", onbu.materials().get("default"));
        assertEquals(3, onbu.animations().size());
        assertEquals(List.of("controller.render.blue_archive"), onbu.renderControllers());
        assertEquals(1, onbu.preAnimation().size());
    }

    /**
     * {@code scripts.animate} holds two shapes in one array, and both carry meaning.
     *
     * <p>A bare string plays unconditionally; an object plays while its Molang is true. An
     * implementation assuming either shape alone reads half of a real file and says nothing about
     * the other half — and the half it drops is the one that decides first person from third.
     */
    @Test
    void readsBothShapesOfTheAnimateList() {
        AttachableIr onbu = parse("""
                {
                  "format_version": "1.16.100",
                  "minecraft:attachable": {
                    "description": {
                      "identifier": "sc:x",
                      "scripts": {
                        "animate": [
                          { "main_hand": "v.main_hand && c.is_first_person" },
                          "hoshino"
                        ]
                      }
                    }
                  }
                }""").get(0);

        assertEquals(2, onbu.animate().size());
        assertEquals("main_hand", onbu.animate().get(0).name());
        // Kept as SOURCE TEXT. SC-110 §7 forbids storing Molang as something evaluable in the IR;
        // it is compiled where something is ready to run it, with provenance to report against.
        assertEquals(Optional.of("v.main_hand && c.is_first_person"),
                onbu.animate().get(0).condition());
        assertEquals("hoshino", onbu.animate().get(1).name());
        assertEquals(Optional.empty(), onbu.animate().get(1).condition());
    }

    @Test
    void anAttachableWithNoIdentifierIsSkippedRatherThanFatal() {
        // Constitution rule 5. Nothing can reference it, and losing one attachable costs less than
        // losing the pack it is in.
        assertTrue(parse("""
                {
                  "format_version": "1.16.100",
                  "minecraft:attachable": { "description": { "materials": {} } }
                }""").isEmpty());
        assertTrue(!diagnostics.snapshot().isEmpty(), "it must say so");
    }

    @Test
    void anEmptyDescriptionStillReadsAsAnAttachable() {
        // Every map is optional in Bedrock's own files: an attachable may name only a geometry, or
        // only textures. Absent maps come back empty rather than as a refusal.
        AttachableIr bare = parse("""
                {
                  "format_version": "1.16.100",
                  "minecraft:attachable": { "description": { "identifier": "sc:x" } }
                }""").get(0);
        assertEquals(Optional.empty(), bare.defaultGeometry());
        assertTrue(bare.animate().isEmpty());
        assertTrue(bare.renderControllers().isEmpty());
    }
}
