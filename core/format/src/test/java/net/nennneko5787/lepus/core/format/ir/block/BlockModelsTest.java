package net.nennneko5787.lepus.core.format.ir.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import org.junit.jupiter.api.Test;

class BlockModelsTest {

    private static Map<BedrockId, JsonValue> components(String json) {
        Map<BedrockId, JsonValue> map = new LinkedHashMap<>();
        Json.parse(json).asObject().orElseThrow().members()
                .forEach((key, value) -> map.put(BedrockId.parse(key), value));
        return map;
    }

    @Test
    void oneTextureForEveryFaceIsRead() {
        BlockModels.Materials materials = BlockModels.materialsOf(components("""
                { "minecraft:material_instances": { "*": { "texture": "magic_block" } } }"""));
        assertEquals(Optional.of("magic_block"), materials.textureFor("up"));
        assertEquals(Optional.of("magic_block"), materials.textureFor("anything"));
    }

    @Test
    void aPerFaceTextureBeatsTheCatchAll() {
        BlockModels.Materials materials = BlockModels.materialsOf(components("""
                {
                  "minecraft:material_instances": {
                    "*": { "texture": "sides" },
                    "up": { "texture": "top" }
                  }
                }"""));
        assertEquals(Optional.of("top"), materials.textureFor("up"));
        assertEquals(Optional.of("sides"), materials.textureFor("north"));
    }

    @Test
    void anInstanceNamingAnotherInstanceCopiesIt() {
        // Bedrock's alias form: "down": "up" means "draw the down face like the up one". Reading
        // only the object form leaves that face with no texture at all.
        BlockModels.Materials materials = BlockModels.materialsOf(components("""
                {
                  "minecraft:material_instances": {
                    "up": { "texture": "top" },
                    "down": "up"
                  }
                }"""));
        assertEquals(Optional.of("top"), materials.textureFor("down"));
    }

    @Test
    void aBlockWithNoGeometryComponentIsAFullCube() {
        // The commonest block in every pack. Treating absent as "unknown shape" would send it down
        // the fallback path for no reason.
        assertTrue(BlockModels.materialsOf(Map.of()).fullCube());
        assertTrue(BlockModels.materialsOf(components(
                "{ \"minecraft:geometry\": \"minecraft:geometry.full_block\" }")).fullCube());
    }

    @Test
    void aCustomGeometryIsNotAFullCube() {
        assertFalse(BlockModels.materialsOf(components(
                "{ \"minecraft:geometry\": \"geometry.magic_block\" }")).fullCube());
        assertFalse(BlockModels.materialsOf(components(
                "{ \"minecraft:geometry\": { \"identifier\": \"geometry.magic\" } }")).fullCube());
    }

    @Test
    void aBlockWithNoMaterialsHasNoTextures() {
        assertTrue(BlockModels.materialsOf(Map.of()).isEmpty());
        assertEquals(Optional.empty(), BlockModels.materialsOf(Map.of()).textureFor("up"));
    }

    @Test
    void aBlockstateGetsOneVariantPerStateIndex() {
        String json = BlockModels.blockstateJson(List.of("lepus:block/a",
                "lepus:block/b"));
        Map<String, JsonValue> variants = Json.parse(json).asObject().orElseThrow()
                .members().get("variants").asObject().orElseThrow().members();
        assertEquals(2, variants.size());
        assertTrue(variants.containsKey("i=0"));
        assertTrue(variants.containsKey("i=1"));
    }

    @Test
    void aSingleStateBlockUsesTheCatchAllKey() {
        // A size-one class carries no property, so there is no `i=` to match on and only the empty
        // key selects anything. Vanilla's own air.json is written this way.
        Map<String, JsonValue> variants =
                Json.parse(BlockModels.blockstateJson(List.of(BlockModels.AIR_MODEL)))
                        .asObject().orElseThrow().members().get("variants")
                        .asObject().orElseThrow().members();
        assertEquals(1, variants.size());
        assertTrue(variants.containsKey(""));
    }

    @Test
    void oneTextureEverywhereProducesCubeAll() {
        Map<String, JsonValue> model = Json.parse(
                BlockModels.cubeModelJson(Map.of("all", "lepus:block/magic")))
                .asObject().orElseThrow().members();
        assertEquals(Optional.of("minecraft:block/cube_all"), model.get("parent").asString());
    }

    /**
     * An item with an attachable keeps its inventory icon.
     *
     * <p><b>The regression this exists for shipped and was seen.</b> Making such an item a plain
     * special model — which is what a shield and a trident are — is the obvious reading and it takes
     * the icon away with it: a special renderer draws in EVERY display context, so the sprite is
     * never drawn and the inventory slot goes blank. Bedrock does not do that; an attachable is what
     * the item looks like in a hand, and {@code minecraft:icon} is what it looks like in a slot.
     *
     * <p>Both halves are asserted because either alone passes for the wrong build: the fallback
     * alone is an item that never shows its model, and the case alone is what shipped.
     *
     * <p>What the hand case CONTAINS changed once the model stopped being the seam: an attachable is
     * posed in player space, not at the hand, so <b>both</b> hand views draw nothing here and the
     * drawing is done by a layer in third person and a hand-render hook in first. Only the empty
     * half is asserted — where the model actually comes from is not this file's to know.
     */
    @Test
    void anItemWithAHeldModelStillHasAnIcon() {
        Map<String, JsonValue> definition = Json.parse(
                BlockModels.heldModelJson("lepus:item/halo_model"))
                .asObject().orElseThrow()
                .members().get("model").asObject().orElseThrow().members();

        assertEquals(Optional.of("minecraft:select"), definition.get("type").asString());
        assertEquals(Optional.of("minecraft:display_context"),
                definition.get("property").asString());
        // The inventory, the ground and the item frame all fall through to the flat sprite.
        assertEquals(Optional.of("lepus:item/halo_model"),
                definition.get("fallback").asObject().orElseThrow()
                        .members().get("model").asString());

        Map<String, JsonValue> held = definition.get("cases").asArray().orElseThrow()
                .values().get(0).asObject().orElseThrow().members();
        List<String> when = held.get("when").asArray().orElseThrow().values().stream()
                .map(value -> value.asString().orElseThrow()).toList();
        // BOTH views. Third person was left out at first, which put a flat sprite in the hand
        // beside the character the layer was drawing on the player.
        assertTrue(when.contains("firstperson_righthand"), when.toString());
        assertTrue(when.contains("thirdperson_righthand"), when.toString());
        // And NOT the inventory, which is the whole point.
        assertFalse(when.contains("gui"), when.toString());
        assertEquals(Optional.of("minecraft:empty"),
                held.get("model").asObject().orElseThrow().members().get("type").asString());
    }

    @Test
    void differingFacesProduceTheSixSidedParentAndAParticleTexture() {
        Map<String, String> faces = new LinkedHashMap<>();
        faces.put("up", "lepus:block/top");
        faces.put("down", "lepus:block/bottom");
        Map<String, JsonValue> model = Json.parse(BlockModels.cubeModelJson(faces))
                .asObject().orElseThrow().members();
        assertEquals(Optional.of("minecraft:block/cube"), model.get("parent").asString());
        // Without a particle texture, breaking the block throws off the missing-texture checker.
        assertTrue(model.get("textures").asObject().orElseThrow()
                .members().containsKey("particle"));
    }
}
