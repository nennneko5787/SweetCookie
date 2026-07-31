package net.nennneko5787.sweetcookie.core.format.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import org.junit.jupiter.api.Test;

/** RFC 6901 pointers, which are how provenance says where in a file something is. SC-110 §4. */
@ProvesSpec("SC-110")
class JsonPointerTest {

    private static final JsonValue DOC = Json.parse("""
            {
              "minecraft:entity": {
                "components": {
                  "minecraft:collision_box": {"width": 0.6, "height": 1.8}
                }
              },
              "a/b": 1,
              "m~n": 2,
              "list": [10, 20, 30]
            }
            """);

    @Test
    @ProvesSpec("SC-110")
    void buildsPointersByDescent() {
        String pointer = JsonPointer.child(
                JsonPointer.child(JsonPointer.ROOT, "minecraft:entity"), "components");
        assertEquals("/minecraft:entity/components", pointer);
        assertEquals("/list/2", JsonPointer.index(JsonPointer.child(JsonPointer.ROOT, "list"), 2));
    }

    @Test
    @ProvesSpec("SC-110")
    void escapesTheTwoReservedCharacters() {
        // Order matters both ways: ~ becomes ~0 first, and ~1 is decoded first.
        assertEquals("a~1b", JsonPointer.escape("a/b"));
        assertEquals("m~0n", JsonPointer.escape("m~n"));
        assertEquals("~01", JsonPointer.escape("~1"));
        assertEquals("~1", JsonPointer.unescape("~01"));
        assertEquals("a/b", JsonPointer.unescape("a~1b"));
    }

    @Test
    @ProvesSpec("SC-110")
    void splitsIntoUnescapedTokens() {
        assertEquals(List.of(), JsonPointer.split(JsonPointer.ROOT));
        assertEquals(List.of("a/b"), JsonPointer.split("/a~1b"));
        assertEquals(List.of("x", "", "y"), JsonPointer.split("/x//y"));
        assertThrows(IllegalArgumentException.class, () -> JsonPointer.split("no-leading-slash"));
    }

    @Test
    @ProvesSpec("SC-110")
    void resolvesObjectsAndArrays() {
        assertEquals(0.6f, JsonPointer
                .resolve(DOC, "/minecraft:entity/components/minecraft:collision_box/width")
                .flatMap(JsonValue::asNumber).orElseThrow().floatValue());
        assertEquals(20f,
                JsonPointer.resolve(DOC, "/list/1").flatMap(JsonValue::asNumber).orElseThrow()
                        .floatValue());
        assertEquals(DOC, JsonPointer.resolve(DOC, JsonPointer.ROOT).orElseThrow());
    }

    @Test
    @ProvesSpec("SC-110")
    void resolvesKeysContainingTheReservedCharacters() {
        assertEquals(1f, JsonPointer.resolve(DOC, "/a~1b").flatMap(JsonValue::asNumber)
                .orElseThrow().floatValue());
        assertEquals(2f, JsonPointer.resolve(DOC, "/m~0n").flatMap(JsonValue::asNumber)
                .orElseThrow().floatValue());
    }

    @Test
    @ProvesSpec("SC-110")
    void returnsEmptyForAnythingItCannotAddress() {
        assertTrue(JsonPointer.resolve(DOC, "/nope").isEmpty());
        assertTrue(JsonPointer.resolve(DOC, "/list/9").isEmpty());
        assertTrue(JsonPointer.resolve(DOC, "/list/01").isEmpty()); // RFC 6901 forbids leading zeroes
        assertTrue(JsonPointer.resolve(DOC, "/list/x").isEmpty());
        assertTrue(JsonPointer.resolve(DOC, "/a~1b/deeper").isEmpty());
    }
}
