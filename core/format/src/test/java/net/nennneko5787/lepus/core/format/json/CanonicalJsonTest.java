package net.nennneko5787.lepus.core.format.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.nennneko5787.lepus.core.api.ProvesSpec;
import org.junit.jupiter.api.Test;

/**
 * Canonical JSON, SC-000 §6.
 *
 * <p>These assertions are byte-exact on purpose. The block ledger's state-schema hash, the upstream
 * lock file and every conformance golden compare canonical JSON, so "close enough" here means a
 * ledger that thinks a schema changed when it did not — and a schema change re-maps placed blocks.
 *
 * <p>Characters outside ASCII are written as code points rather than as literals. An invisible
 * U+0001 in a source file is a maintenance hazard, and a test whose input cannot be read is a test
 * nobody can fix.
 */
@ProvesSpec("SC-000")
class CanonicalJsonTest {

    private static final String E_ACUTE = String.valueOf((char) 0xE9);          // U+00E9
    private static final String REPLACEMENT = String.valueOf((char) 0xFFFD);    // U+FFFD
    private static final String GRINNING = new String(Character.toChars(0x1F600));

    private static String canon(String input) {
        return CanonicalJson.write(Json.parse(input));
    }

    @Test
    @ProvesSpec("SC-000")
    void sortsKeysAndStripsWhitespace() {
        assertEquals("{\"a\":1,\"b\":2,\"z\":3}", canon("{ \"z\": 3,\n \"a\": 1,  \"b\": 2 }"));
    }

    @Test
    @ProvesSpec("SC-000")
    void sortsByCodePointNotByUtf16CodeUnit() {
        // U+1F600 is above U+FFFD by code point but below it by UTF-16 code unit, because its
        // surrogate pair starts at U+D83D. String.compareTo gets this backwards, which is why
        // KEY_ORDER is not String.compareTo.
        String out = canon("{\"" + GRINNING + "\": 1, \"" + REPLACEMENT + "\": 2}");
        assertEquals("{\"" + REPLACEMENT + "\":2,\"" + GRINNING + "\":1}", out);
        assertNotEquals("{\"" + GRINNING + "\":1,\"" + REPLACEMENT + "\":2}", out);
    }

    @Test
    @ProvesSpec("SC-000")
    void normalisesNumberSpelling() {
        // Canonicalisation is deliberately lossy about the literal: a golden diff should show a
        // changed value, never a changed spelling.
        assertEquals("[1,1,1,1]", canon("[1, 1.0, 1e0, 0001]"));
        assertEquals("[-3,0.5,1.0E20]", canon("[-3, 0.5, 1e20]"));
    }

    @Test
    @ProvesSpec("SC-000")
    void keepsNegativeZeroDistinct() {
        // Collapsing -0 to 0 would make the canonical form non-injective, which is the one thing a
        // hash rule must not be.
        assertEquals("[0,-0]", canon("[0, -0.0]"));
    }

    @Test
    @ProvesSpec("SC-000")
    void escapesMinimally() {
        String input = "q\"b\\s\nt\tu" + (char) 0x01 + "v/" + E_ACUTE;
        String expected = "\"q\\\"b\\\\s\\nt\\tu\\u0001v/" + E_ACUTE + "\"";
        assertEquals(expected, CanonicalJson.write(new JsonString(input)));
    }

    @Test
    @ProvesSpec("SC-000")
    void refusesValuesJsonCannotExpress() {
        assertThrows(IllegalArgumentException.class, () -> JsonNumber.of(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> JsonNumber.of(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> JsonNumber.ofLiteral("NaN"));
    }

    @Test
    @ProvesSpec("SC-000")
    void isIdempotent() {
        String once = canon("""
                {"b": [3, {"y": 1, "x": 2}], "a": {"nested": {"deep": "  spaced  "}}}
                """);
        assertEquals(once, canon(once));
    }

    @Test
    @ProvesSpec("SC-110")
    void numbersCompareByValueAndKeepTheirLiteral() {
        JsonNumber written = JsonNumber.ofLiteral("1.50");
        assertEquals(JsonNumber.of(1.5d), written);
        assertEquals("1.50", written.literal());
        assertEquals(1.5f, written.floatValue());
        assertEquals(1, written.intValue()); // truncated toward zero, matching Bedrock
        assertEquals(-1, JsonNumber.of(-1.9d).intValue());
    }
}
