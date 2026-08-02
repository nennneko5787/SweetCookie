package net.nennneko5787.lepus.core.format.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.diag.FormatDiagnostics;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.Provenance;
import org.junit.jupiter.api.Test;

/**
 * The Bedrock JSON dialect, SC-110 §2.1.
 *
 * <p>Every leniency asserted here is one that real packs need. The tests are written as "this input,
 * which strict JSON rejects, must parse" rather than as parser internals, so that swapping the
 * backend later re-runs the same contract.
 */
@ProvesSpec("SC-110")
class JsonReaderTest {

    private static JsonObject object(String text) {
        return Json.parse(text).asObject().orElseThrow();
    }

    @Test
    @ProvesSpec("SC-110")
    void parsesTheSixShapes() {
        JsonObject root = object("""
                {"o": {}, "a": [], "s": "x", "n": 1.5, "b": true, "z": null}
                """);
        assertTrue(root.getObject("o").isPresent());
        assertTrue(root.getArray("a").isPresent());
        assertEquals("x", root.getString("s").orElseThrow());
        assertEquals(1.5f, root.getFloat("n").orElseThrow());
        assertEquals(true, root.getBool("b").orElseThrow());
        assertTrue(root.get("z").orElseThrow().isNull());
    }

    @Test
    @ProvesSpec("SC-110")
    void stripsAByteOrderMark() {
        // Packs authored on Windows carry these constantly, and every strict parser rejects them.
        JsonObject root = object('\uFEFF' + "{\"format_version\": \"1.8.0\"}");
        assertEquals("1.8.0", root.getString("format_version").orElseThrow());
    }

    @Test
    @ProvesSpec("SC-110")
    void acceptsLineAndBlockComments() {
        JsonObject root = object("""
                {
                  // Blockbench writes these.
                  "a": 1, /* and these */
                  "b": /* even here */ 2
                }
                """);
        assertEquals(1f, root.getFloat("a").orElseThrow());
        assertEquals(2f, root.getFloat("b").orElseThrow());
    }

    @Test
    @ProvesSpec("SC-110")
    void acceptsTrailingCommas() {
        assertEquals(2, object("{\"a\": 1, \"b\": 2,}").size());
        assertEquals(3, Json.parse("[1, 2, 3,]").asArray().orElseThrow().size());
    }

    @Test
    @ProvesSpec("SC-110")
    void acceptsRawControlCharactersInStrings() {
        // A literal newline inside a string is illegal JSON and appears in real description fields.
        JsonObject root = object("{\"description\": \"line one\nline two\"}");
        assertEquals("line one\nline two", root.getString("description").orElseThrow());
    }

    @Test
    @ProvesSpec("SC-110")
    void countsLinesThroughRawNewlinesAndComments() {
        // If line accounting does not advance inside strings and comments, every diagnostic after
        // the first multi-line value points at the wrong line — which is worse than none.
        JsonParseException e = assertThrows(JsonParseException.class, () -> Json.parse("""
                {
                  "a": "one
                two",
                  /* three
                     four */
                  "b": }
                """));
        assertEquals(6, e.line());
    }

    @Test
    @ProvesSpec("SC-110")
    void acceptsLeadingZeroesAndAPlusSign() {
        JsonObject root = object("{\"a\": 007, \"b\": +5}");
        assertEquals(7f, root.getFloat("a").orElseThrow());
        assertEquals(5f, root.getFloat("b").orElseThrow());
    }

    @Test
    @ProvesSpec("SC-110")
    void keepsUnrecognisedEscapesVerbatim() {
        // Dropping the backslash would silently rewrite a Windows path; dropping the pair would
        // lose bytes. Keeping both cannot lose anything.
        assertEquals("C:\\Users\\x", object("{\"p\": \"C:\\Users\\x\"}").getString("p").orElseThrow());
        assertEquals("\\uZZZZ", object("{\"p\": \"\\uZZZZ\"}").getString("p").orElseThrow());
    }

    @Test
    @ProvesSpec("SC-110")
    void decodesTheStandardEscapes() {
        JsonObject root = object("{\"p\": \"a\\tb\\nc\\\"d\\\\e\\/f\\u00e9\"}");
        assertEquals("a\tb\nc\"d\\e/fé", root.getString("p").orElseThrow());
    }

    @Test
    @ProvesSpec("SC-110")
    void preservesMemberOrder() {
        // SC-110 §10: the ledger depends on a deterministic walk, and a HashMap here would make it
        // non-deterministic — which corrupts worlds rather than producing an odd diff.
        assertEquals(List.of("z", "m", "a"), List.copyOf(object("{\"z\":1,\"m\":2,\"a\":3}").keys()));
    }

    @Test
    @ProvesSpec("SC-110")
    void rejectsDuplicateKeys() {
        // SC-000 §6.6. Last-wins would hide an authoring bug forever.
        JsonParseException e = assertThrows(JsonParseException.class,
                () -> Json.parse("{\"minecraft:collision_box\": {}, \"minecraft:collision_box\": {}}"));
        assertEquals(JsonParseException.Kind.DUPLICATE_KEY, e.kind());
    }

    @Test
    @ProvesSpec("SC-110")
    void rejectsNestingBeyondTheLimit() {
        String deep = "[".repeat(40) + "]".repeat(40);
        JsonParseException e = assertThrows(JsonParseException.class,
                () -> Json.parse(deep, new JsonLimits(8)));
        assertEquals(JsonParseException.Kind.TOO_DEEP, e.kind());
        // The same document is fine under the default limit, so the guard is a limit and not a bug.
        assertEquals(1, Json.parse(deep).asArray().orElseThrow().size());
    }

    @Test
    @ProvesSpec("SC-110")
    void rejectsStructuralDamage() {
        assertEquals(JsonParseException.Kind.MALFORMED,
                assertThrows(JsonParseException.class, () -> Json.parse("")).kind());
        assertEquals(JsonParseException.Kind.MALFORMED,
                assertThrows(JsonParseException.class, () -> Json.parse("{\"a\": 1")).kind());
        assertEquals(JsonParseException.Kind.MALFORMED,
                assertThrows(JsonParseException.class, () -> Json.parse("[1, 2")).kind());
        assertEquals(JsonParseException.Kind.MALFORMED,
                assertThrows(JsonParseException.class, () -> Json.parse("{\"a\": 1} trailing")).kind());
        assertEquals(JsonParseException.Kind.MALFORMED,
                assertThrows(JsonParseException.class, () -> Json.parse("{\"a\": 1 /* unclosed")).kind());
        assertEquals(JsonParseException.Kind.MALFORMED,
                assertThrows(JsonParseException.class, () -> Json.parse("{a: 1}")).kind());
    }

    @Test
    @ProvesSpec("SC-110")
    void reportsLineAndColumn() {
        JsonParseException e = assertThrows(JsonParseException.class, () -> Json.parse("""
                {
                  "a": 1,
                  "b": %
                }
                """));
        assertEquals(3, e.line());
        assertEquals(8, e.column());
        assertTrue(e.getMessage().contains("line 3"), e.getMessage());
    }

    @Test
    @ProvesSpec("SC-110")
    void tryParseTurnsAFailureIntoADiagnosticRatherThanAThrow() {
        // Constitution rule 1: a structurally broken file is skipped, the rest of the pack loads.
        Diagnostics diagnostics = new Diagnostics();
        Provenance where = Provenance.file(PackId.derived("test"), "entities/broken.json");

        assertTrue(Json.tryParse("{ oops", where, diagnostics).isEmpty());

        var reported = diagnostics.snapshot().diagnostics();
        assertEquals(1, reported.size());
        assertEquals(FormatDiagnostics.JSON_MALFORMED.code(), reported.get(0).code());
        assertEquals(where, reported.get(0).where().orElseThrow());
    }

    @Test
    @ProvesSpec("SC-110")
    void tryParseObjectRejectsANonObjectRoot() {
        Diagnostics diagnostics = new Diagnostics();
        assertTrue(Json.tryParseObject("[1, 2]", Provenance.NONE, diagnostics).isEmpty());
        assertFalse(diagnostics.snapshot().withCode(FormatDiagnostics.JSON_MALFORMED.code()).isEmpty());
    }

    @Test
    @ProvesSpec("SC-110")
    void reportsTheRightCodePerFailureKind() {
        Diagnostics diagnostics = new Diagnostics();
        Json.tryParse("{\"a\":1,\"a\":2}", Provenance.file(PackId.NONE, "a.json"), diagnostics);
        Json.tryParse("[[[[]]]]", new JsonLimits(2),
                Provenance.file(PackId.NONE, "b.json"), diagnostics);

        var log = diagnostics.snapshot();
        assertEquals(1, log.withCode(FormatDiagnostics.JSON_DUPLICATE_KEY.code()).size());
        assertEquals(1, log.withCode(FormatDiagnostics.JSON_TOO_DEEP.code()).size());
    }
}
