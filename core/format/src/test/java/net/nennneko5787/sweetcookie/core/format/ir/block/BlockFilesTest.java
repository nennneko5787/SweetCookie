package net.nennneko5787.sweetcookie.core.format.ir.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostics;
import net.nennneko5787.sweetcookie.core.format.ir.IrDiagnostics;
import net.nennneko5787.sweetcookie.core.format.json.Json;
import net.nennneko5787.sweetcookie.core.format.json.JsonObject;
import net.nennneko5787.sweetcookie.core.format.value.BedrockId;
import net.nennneko5787.sweetcookie.core.format.value.PackId;
import net.nennneko5787.sweetcookie.core.format.value.Provenance;
import org.junit.jupiter.api.Test;

/** Block definitions, states and permutation resolution. SC-150. */
@ProvesSpec("SC-150")
class BlockFilesTest {

    private static final Provenance WHERE =
            Provenance.file(PackId.NONE, "blocks/sc_conformance_lamp.json");

    private Diagnostics diagnostics = new Diagnostics();

    private BlockDefIr parse(String json) {
        diagnostics = new Diagnostics();
        JsonObject root = Json.parse(json).asObject().orElseThrow();
        return BlockFiles.parse(root, WHERE, diagnostics).get(0);
    }

    private boolean reported(int code) {
        return !diagnostics.snapshot().withCode(code).isEmpty();
    }

    private static BedrockId id(String s) {
        return BedrockId.parse(s);
    }

    // ── States and the index ─────────────────────────────────────────────────────────────────

    @Test
    @ProvesSpec("SC-150")
    void readsBothStateSpellings() {
        BlockDefIr block = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:block": {
                    "description": {
                      "identifier": "sc_conformance:lamp",
                      "states": {
                        "sc:lit": [false, true],
                        "sc:level": { "values": { "min": 0, "max": 3 } },
                        "sc:kind": ["short", "tall"]
                      }
                    },
                    "components": {}
                  }
                }
                """);

        assertEquals(3, block.schema().states().size());
        assertEquals(BlockStateIr.Kind.BOOLEAN,
                block.schema().state(id("sc:lit")).orElseThrow().kind());
        assertEquals(BlockStateIr.Kind.INTEGER,
                block.schema().state(id("sc:level")).orElseThrow().kind());
        assertEquals(List.of("0", "1", "2", "3"),
                block.schema().state(id("sc:level")).orElseThrow().values());
        assertEquals(2 * 4 * 2, block.schema().size());
    }

    @Test
    @ProvesSpec("SC-150")
    void encodesStatesAsMixedRadixInDeclarationOrder() {
        // The index reaches chunk storage, so declaration order is part of the on-disk format:
        // reordering a pack's states re-maps every block already placed in every world.
        BlockStateSchema schema = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:block": {
                    "description": {
                      "identifier": "sc_conformance:lamp",
                      "states": { "sc:lit": [false, true],
                                  "sc:level": { "values": { "min": 0, "max": 3 } } }
                    },
                    "components": {}
                  }
                }
                """).schema();

        assertEquals(0, schema.encode(Map.of(id("sc:lit"), "false", id("sc:level"), "0")));
        assertEquals(1, schema.encode(Map.of(id("sc:lit"), "true", id("sc:level"), "0")));
        assertEquals(2, schema.encode(Map.of(id("sc:lit"), "false", id("sc:level"), "1")));
        assertEquals(7, schema.encode(Map.of(id("sc:lit"), "true", id("sc:level"), "3")));

        for (int i = 0; i < schema.size(); i++) {
            assertEquals(i, schema.encode(schema.decode(i)), "round trip at " + i);
        }
    }

    @Test
    @ProvesSpec("SC-150")
    void fallsBackToTheDefaultForAValueAStateDoesNotPermit() {
        // A typo in one permutation costs that permutation, not the block. Bedrock falls back too.
        BlockStateSchema schema = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:block": {
                    "description": { "identifier": "sc_conformance:lamp",
                                     "states": { "sc:kind": ["short", "tall"] } },
                    "components": {}
                  }
                }
                """).schema();
        assertEquals(0, schema.encode(Map.of(id("sc:kind"), "enormous")));
        assertEquals(0, schema.encode(Map.of()));
    }

    @Test
    @ProvesSpec("SC-150")
    void truncatesAStateBeyondBedrocksSixteenValueCap() {
        BlockDefIr block = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:block": {
                    "description": { "identifier": "sc_conformance:lamp",
                                     "states": { "sc:many": { "values": { "min": 0, "max": 20 } } } },
                    "components": {}
                  }
                }
                """);
        assertEquals(BlockStateIr.MAX_VALUES, block.schema().size());
        assertTrue(reported(IrDiagnostics.FIELD_MALFORMED.code()));
    }

    @Test
    @ProvesSpec("SC-150")
    void appendsTraitStatesAfterDeclaredOnes() {
        // Appending rather than interleaving means enabling a trait cannot shift the digits of the
        // states already encoded in placed blocks.
        BlockDefIr block = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:block": {
                    "description": {
                      "identifier": "sc_conformance:lamp",
                      "states": { "sc:lit": [false, true] },
                      "traits": {
                        "minecraft:placement_direction": {
                          "enabled_states": ["minecraft:cardinal_direction"]
                        }
                      }
                    },
                    "components": {}
                  }
                }
                """);

        assertEquals(List.of("sc:lit", "minecraft:cardinal_direction"),
                block.schema().states().stream().map(s -> s.name().toString()).toList());
        assertEquals(2 * 4, block.schema().size());
    }

    // ── Permutation resolution, driven by real Molang ────────────────────────────────────────

    @Test
    @ProvesSpec("SC-150")
    void resolvesPermutationsPerStateIndex() {
        BlockDefIr block = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:block": {
                    "description": {
                      "identifier": "sc_conformance:lamp",
                      "states": { "sc:level": { "values": { "min": 0, "max": 3 } } }
                    },
                    "components": { "minecraft:friction": 0.6 },
                    "permutations": [
                      { "condition": "query.block_state('sc:level') > 1",
                        "components": { "minecraft:light_emission": 7 } },
                      { "condition": "query.block_state('sc:level') == 3",
                        "components": { "minecraft:light_emission": 15,
                                        "minecraft:map_color": "#FFFFFF" } }
                    ]
                  }
                }
                """);

        assertFalse(reported(IrDiagnostics.FIELD_MALFORMED.code()));
        assertEquals(4, block.schema().size());

        // level 0 and 1: base only.
        assertEquals(List.of(id("minecraft:friction")), List.copyOf(block.resolve(0).keySet()));
        assertEquals(List.of(id("minecraft:friction")), List.copyOf(block.resolve(1).keySet()));
        // level 2: the first permutation matches.
        assertTrue(block.resolve(2).containsKey(id("minecraft:light_emission")));
        assertFalse(block.resolve(2).containsKey(id("minecraft:map_color")));
        // level 3: both match, and the LATER one wins per key - Bedrock's rule.
        Map<BedrockId, ?> top = block.resolve(3);
        assertTrue(top.containsKey(id("minecraft:map_color")));
        assertEquals("15", String.valueOf(
                ((net.nennneko5787.sweetcookie.core.format.json.JsonNumber)
                        top.get(id("minecraft:light_emission"))).intValue()));
    }

    @Test
    @ProvesSpec("SC-150")
    void comparesStringStatesByEquality() {
        // A string state answers with the same interned identity a Molang string literal compiles
        // to, which is the only reason `== 'tall'` can work in a float-typed language.
        BlockDefIr block = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:block": {
                    "description": { "identifier": "sc_conformance:lamp",
                                     "states": { "sc:kind": ["short", "tall"] } },
                    "components": {},
                    "permutations": [
                      { "condition": "query.block_state('sc:kind') == 'tall'",
                        "components": { "minecraft:collision_box": false } }
                    ]
                  }
                }
                """);

        assertFalse(block.resolve(0).containsKey(id("minecraft:collision_box")));
        assertTrue(block.resolve(1).containsKey(id("minecraft:collision_box")));
    }

    @Test
    @ProvesSpec("SC-150")
    void treatsAnyNonZeroConditionAsTrue() {
        // A condition written as `q.block_state('level') - 1` means "level is not 1", and packs
        // write it that way.
        BlockDefIr block = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:block": {
                    "description": { "identifier": "sc_conformance:lamp",
                                     "states": { "sc:level": { "values": { "min": 0, "max": 2 } } } },
                    "components": {},
                    "permutations": [
                      { "condition": "query.block_state('sc:level') - 1",
                        "components": { "minecraft:friction": 0.1 } }
                    ]
                  }
                }
                """);

        assertTrue(block.resolve(0).containsKey(id("minecraft:friction")), "0 - 1 is -1, truthy");
        assertFalse(block.resolve(1).containsKey(id("minecraft:friction")), "1 - 1 is 0, falsy");
        assertTrue(block.resolve(2).containsKey(id("minecraft:friction")));
    }

    @Test
    @ProvesSpec("SC-150")
    void dropsAPermutationWhoseConditionWillNotCompile() {
        // Not defaulted to always-match or never-match: both are wrong, and each silently changes
        // what the block looks like in half its states.
        BlockDefIr block = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:block": {
                    "description": { "identifier": "sc_conformance:lamp",
                                     "states": { "sc:lit": [false, true] } },
                    "components": {},
                    "permutations": [
                      { "condition": "query.block_state('sc:lit') &&",
                        "components": { "minecraft:friction": 0.1 } },
                      { "condition": "query.block_state('sc:lit')",
                        "components": { "minecraft:light_emission": 15 } }
                    ]
                  }
                }
                """);

        assertEquals(1, block.permutations().size());
        assertTrue(reported(IrDiagnostics.FIELD_MALFORMED.code()));
        assertTrue(block.resolve(1).containsKey(id("minecraft:light_emission")));
        assertFalse(block.resolve(1).containsKey(id("minecraft:friction")));
    }

    @Test
    @ProvesSpec("SC-150")
    void reportsAConditionReachingForStateItCannotSee() {
        // A permutation condition may read block state and pure maths and nothing else, which is
        // what makes the whole set pre-resolvable per index at bind time.
        parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:block": {
                    "description": { "identifier": "sc_conformance:lamp" },
                    "components": {},
                    "permutations": [
                      { "condition": "math.nonexistent(1)", "components": {} }
                    ]
                  }
                }
                """);
        assertTrue(reported(IrDiagnostics.FIELD_MALFORMED.code()));
    }

    @Test
    @ProvesSpec("SC-150")
    void aBlockWithNoStatesHasExactlyOneIndex() {
        BlockDefIr block = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:block": {
                    "description": { "identifier": "sc_conformance:plain" },
                    "components": { "minecraft:friction": 0.6 }
                  }
                }
                """);
        assertEquals(1, block.schema().size());
        assertTrue(block.isUniform());
        assertEquals(1, block.resolveAll().size());
    }
}
