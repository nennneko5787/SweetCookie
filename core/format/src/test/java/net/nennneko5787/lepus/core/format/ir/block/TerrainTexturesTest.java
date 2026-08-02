package net.nennneko5787.lepus.core.format.ir.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import net.nennneko5787.lepus.core.format.json.Json;
import org.junit.jupiter.api.Test;

class TerrainTexturesTest {

    private static TerrainTextures read(String json) {
        return TerrainTextures.of(Json.parse(json));
    }

    @Test
    void aStringValueIsThePath() {
        assertEquals(Optional.of("textures/blocks/magic"), read("""
                { "texture_data": { "magic": { "textures": "textures/blocks/magic" } } }""")
                .resolve("magic"));
    }

    @Test
    void anObjectValueCarriesThePathBesideColourData() {
        // Mojang's own samples use this for tinted blocks. Reading only the string form leaves a
        // working pack looking broken.
        assertEquals(Optional.of("textures/blocks/leaves"), read("""
                {
                  "texture_data": {
                    "leaves": {
                      "textures": { "path": "textures/blocks/leaves", "overlay_color": "#79c05a" }
                    }
                  }
                }""").resolve("leaves"));
    }

    @Test
    void anArrayIsAVariantListAndTheFirstOneIsUsed() {
        // Bedrock picks between variants per position with a hash. Picking the first is a visible
        // simplification and a visible block; drawing nothing would be neither.
        assertEquals(Optional.of("textures/blocks/a"), read("""
                { "texture_data": { "stone": { "textures": [
                    "textures/blocks/a", "textures/blocks/b" ] } } }""").resolve("stone"));
    }

    @Test
    void anUnknownKeyThatLooksLikeAPathIsUsedAsOne() {
        // Packs do write a direct path where a key was expected, and Bedrock renders those.
        assertEquals(Optional.of("textures/blocks/direct"),
                TerrainTextures.EMPTY.resolve("textures/blocks/direct"));
    }

    @Test
    void anUnknownKeyThatIsJustANameResolvesToNothing() {
        assertEquals(Optional.empty(), TerrainTextures.EMPTY.resolve("magic"));
    }

    @Test
    void anEntryWithNoUsablePathIsSkippedAndTheRestSurvive() {
        // One malformed entry costs that texture, not the file. Constitution rule 5.
        TerrainTextures textures = read("""
                {
                  "texture_data": {
                    "broken": { "textures": 42 },
                    "fine": { "textures": "textures/blocks/fine" }
                  }
                }""");
        assertEquals(1, textures.size());
        assertEquals(Optional.of("textures/blocks/fine"), textures.resolve("fine"));
    }

    @Test
    void aFileWithNoTextureDataIsEmptyRatherThanAFailure() {
        assertEquals(0, read("{ \"resource_pack_name\": \"vanilla\" }").size());
        assertEquals(0, read("[]").size());
    }
}
