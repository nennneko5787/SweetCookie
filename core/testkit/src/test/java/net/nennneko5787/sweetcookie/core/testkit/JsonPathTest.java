package net.nennneko5787.sweetcookie.core.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import net.nennneko5787.sweetcookie.core.format.json.Json;
import net.nennneko5787.sweetcookie.core.format.json.JsonValue;
import org.junit.jupiter.api.Test;

/** The {@code assert.that} path syntax. */
@ProvesSpec("SC-100")
class JsonPathTest {

    private static final JsonValue DOC = Json.parse("""
            {
              "ir": {
                "packs": [
                  { "id": "a", "texts": { "entries": { "en_US": { "pack.name": "Wizardry" } } },
                    "files": ["manifest.json", "textures/a.png"] }
                ]
              }
            }
            """);

    @Test
    @ProvesSpec("SC-100")
    void walksObjectsAndArrays() {
        assertEquals("a", JsonPath.resolve(DOC, "ir.packs[0].id")
                .flatMap(JsonValue::asString).orElseThrow());
        assertEquals("textures/a.png", JsonPath.resolve(DOC, "ir.packs[0].files[1]")
                .flatMap(JsonValue::asString).orElseThrow());
    }

    @Test
    @ProvesSpec("SC-100")
    void needsQuotesOnlyForKeysContainingASeparator() {
        // Bedrock keys contain dots constantly, which is the whole reason bracket syntax exists.
        assertEquals("Wizardry",
                JsonPath.resolve(DOC, "ir.packs[0].texts.entries['en_US']['pack.name']")
                        .flatMap(JsonValue::asString).orElseThrow());
        assertEquals("Wizardry",
                JsonPath.resolve(DOC, "ir.packs[0].texts.entries[en_US][\"pack.name\"]")
                        .flatMap(JsonValue::asString).orElseThrow());
    }

    @Test
    @ProvesSpec("SC-100")
    void returnsEmptyForAnythingItCannotAddress() {
        assertTrue(JsonPath.resolve(DOC, "ir.packs[9]").isEmpty());
        assertTrue(JsonPath.resolve(DOC, "ir.nope.deeper").isEmpty());
        assertTrue(JsonPath.resolve(DOC, "ir.packs[0].id.deeper").isEmpty());
    }

    @Test
    @ProvesSpec("SC-100")
    void rejectsAMalformedExpressionRatherThanFailingTheCase() {
        // A typo in a case file is a broken test, not a failing one, and the two deserve different
        // messages - otherwise a mistyped path reads as "the implementation regressed".
        assertThrows(IllegalArgumentException.class, () -> JsonPath.split("a[0"));
        assertThrows(IllegalArgumentException.class, () -> JsonPath.split("a]"));
        assertThrows(IllegalArgumentException.class, () -> JsonPath.split("a[]"));
        assertThrows(IllegalArgumentException.class, () -> JsonPath.split(""));
    }

    @Test
    @ProvesSpec("SC-100")
    void splitsIntoTokens() {
        assertEquals(List.of("ir", "packs", "0", "pack.name"),
                JsonPath.split("ir.packs[0]['pack.name']"));
    }
}
