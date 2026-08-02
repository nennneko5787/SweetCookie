package net.nennneko5787.lepus.core.format.ir.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import org.junit.jupiter.api.Test;

class BlockPhysicsTest {

    private static Map<BedrockId, JsonValue> components(String json) {
        return Json.parse(json).asObject().orElseThrow().members().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> BedrockId.parse(entry.getKey()),
                        Map.Entry::getValue,
                        (a, b) -> b,
                        java.util.LinkedHashMap::new));
    }

    @Test
    void aBlockWithNoComponentsGetsBedrocksDefaults() {
        assertEquals(BlockPhysics.DEFAULT, BlockPhysics.of(Map.of()));
    }

    @Test
    void miningTimeAndBlastResistanceAreRead() {
        BlockPhysics physics = BlockPhysics.of(components("""
                {
                  "minecraft:destructible_by_mining": { "seconds_to_destroy": 3.5 },
                  "minecraft:destructible_by_explosion": { "explosion_resistance": 12 }
                }"""));
        assertEquals(Optional.of(3.5f), physics.destroySeconds());
        assertEquals(Optional.of(12.0f), physics.explosionResistance());
        assertFalse(physics.unbreakable());
    }

    @Test
    void falseMeansIndestructible() {
        // The shorthand real packs use for bedrock-like blocks. An object with a number and a bare
        // `false` are the same component; reading only the object form silently makes the block
        // breakable in one hit, which is the opposite of what the pack asked for.
        BlockPhysics physics = BlockPhysics.of(components("""
                {
                  "minecraft:destructible_by_mining": false,
                  "minecraft:destructible_by_explosion": false
                }"""));
        assertTrue(physics.unbreakable());
        assertEquals(Optional.empty(), physics.explosionResistance());
        assertEquals(-1.0f, physics.hardness());
    }

    @Test
    void lightEmissionIsClampedToWhatMinecraftCanRepresent() {
        assertEquals(15, BlockPhysics.of(
                components("{\"minecraft:light_emission\": 42}")).lightEmission());
        assertEquals(0, BlockPhysics.of(
                components("{\"minecraft:light_emission\": -3}")).lightEmission());
        assertEquals(9, BlockPhysics.of(
                components("{\"minecraft:light_emission\": 9}")).lightEmission());
    }

    @Test
    void aComponentOfTheWrongTypeIsTheDefaultRatherThanAFailure() {
        // Constitution rule 5. One typo costs that one value and nothing else.
        BlockPhysics physics = BlockPhysics.of(components("""
                {
                  "minecraft:light_emission": "bright",
                  "minecraft:friction": [],
                  "minecraft:destructible_by_mining": { "seconds_to_destroy": 2 }
                }"""));
        assertEquals(0, physics.lightEmission());
        assertEquals(0.4f, physics.friction());
        assertEquals(Optional.of(2.0f), physics.destroySeconds());
    }

    @Test
    void anEmptyDestructibleObjectFallsBackToInstantMining() {
        assertEquals(Optional.of(0.0f), BlockPhysics.of(
                components("{\"minecraft:destructible_by_mining\": {}}")).destroySeconds());
    }

    @Test
    void frictionIsReadWhenGiven() {
        assertEquals(0.98f, BlockPhysics.of(
                components("{\"minecraft:friction\": 0.98}")).friction());
    }
}
