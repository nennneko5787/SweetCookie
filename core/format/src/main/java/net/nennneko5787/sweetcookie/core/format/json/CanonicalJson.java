package net.nennneko5787.sweetcookie.core.format.json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * Canonical JSON, SC-000 §6.
 *
 * <p>Three things in this project hash or compare JSON and they must all agree: the block ledger's
 * state-schema hash (SC-120), the upstream lock file, and conformance goldens. "Agree" means byte
 * identity, so the rules are fixed rather than left to a pretty-printer's defaults.
 *
 * <ol>
 *   <li>UTF-8, no byte-order mark.
 *   <li>Object keys sorted by Unicode code point, ascending.
 *   <li>No insignificant whitespace at all, and no trailing newline.
 *   <li>Numbers as the shortest representation round-tripping to the same {@code double}; integral
 *       values within ±2^53 without a decimal point or exponent.
 *   <li>Strings escaped minimally.
 * </ol>
 *
 * <p>Note rule 4: canonicalisation is <em>lossy with respect to the literal</em>. {@code 1.0},
 * {@code 1} and {@code 1e0} all become {@code 1}. That is the point — a golden diff should show a
 * changed value, not a changed spelling.
 *
 * <p>Sorting is by code point, not by {@link String#compareTo}, which orders by UTF-16 code unit and
 * therefore sorts astral characters below U+E000. No real Bedrock key is affected; the difference is
 * implemented anyway because a hash rule with a rare exception is a hash rule that breaks once, in
 * production, on somebody else's machine.
 */
@SpecImpl("SC-000")
public final class CanonicalJson {

    /** Ascending Unicode code point order, as SC-000 §6.2 requires. */
    public static final Comparator<String> KEY_ORDER = CanonicalJson::compareByCodePoint;

    private CanonicalJson() {
    }

    public static String write(JsonValue value) {
        StringBuilder sb = new StringBuilder();
        writeTo(sb, value);
        return sb.toString();
    }

    /**
     * The same content, indented, for a file a human has to review.
     *
     * <p>Rule 3 — no insignificant whitespace — exists so that hashing and comparison are exact. It
     * is wrong for a conformance golden: a one-line file makes {@code git diff} useless, and the
     * whole reason goldens are canonical is that their diffs should be readable. So goldens are
     * stored in this form and <b>compared after canonicalising both sides</b>, which keeps the
     * comparison exact while leaving the file reviewable. Key order and number spelling are
     * identical to {@link #write}.
     */
    public static String pretty(JsonValue value) {
        StringBuilder sb = new StringBuilder();
        prettyTo(sb, value, 0);
        return sb.append('\n').toString();
    }

    private static void prettyTo(StringBuilder sb, JsonValue value, int depth) {
        String indent = "  ".repeat(depth + 1);
        String closing = "  ".repeat(depth);
        switch (value) {
            case JsonObject o when o.isEmpty() -> sb.append("{}");
            case JsonObject o -> {
                List<String> keys = new ArrayList<>(o.keys());
                keys.sort(KEY_ORDER);
                sb.append("{\n");
                for (int i = 0; i < keys.size(); i++) {
                    sb.append(indent);
                    string(sb, keys.get(i));
                    sb.append(": ");
                    prettyTo(sb, o.members().get(keys.get(i)), depth + 1);
                    sb.append(i + 1 < keys.size() ? ",\n" : "\n");
                }
                sb.append(closing).append('}');
            }
            case JsonArray a when a.isEmpty() -> sb.append("[]");
            case JsonArray a -> {
                sb.append("[\n");
                for (int i = 0; i < a.size(); i++) {
                    sb.append(indent);
                    prettyTo(sb, a.values().get(i), depth + 1);
                    sb.append(i + 1 < a.size() ? ",\n" : "\n");
                }
                sb.append(closing).append(']');
            }
            default -> writeTo(sb, value);
        }
    }

    private static void writeTo(StringBuilder sb, JsonValue value) {
        switch (value) {
            case JsonObject o -> {
                sb.append('{');
                List<String> keys = new ArrayList<>(o.keys());
                keys.sort(KEY_ORDER);
                boolean first = true;
                for (String key : keys) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    string(sb, key);
                    sb.append(':');
                    writeTo(sb, o.members().get(key));
                }
                sb.append('}');
            }
            case JsonArray a -> {
                sb.append('[');
                for (int i = 0; i < a.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    writeTo(sb, a.values().get(i));
                }
                sb.append(']');
            }
            case JsonString s -> string(sb, s.value());
            case JsonNumber n -> sb.append(number(n.doubleValue()));
            case JsonBool b -> sb.append(b.value() ? "true" : "false");
            case JsonNull ignored -> sb.append("null");
        }
    }

    /** SC-000 §6.4. Package-visible so {@link JsonNumber} can use the same rule for its literal. */
    static String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("not representable in JSON: " + value);
        }
        if (value == 0.0) {
            // Negative zero is a distinct double and JSON can express it. Emitting "0" would make
            // the canonical form non-injective, which is exactly what a hash rule must not be.
            return Double.doubleToRawLongBits(value) == 0L ? "0" : "-0";
        }
        if (value == Math.rint(value) && Math.abs(value) <= 9007199254740992.0) {
            return Long.toString((long) value);
        }
        // Shortest round-tripping form since JDK 19 (JDK-4511638). Its output — "1.0E20", "1.0E-5" —
        // is already valid JSON, so no rewriting is needed.
        return Double.toString(value);
    }

    /** SC-000 §6.5: only {@code "}, {@code \} and code points below U+0020 are escaped. */
    static void string(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append("\\u00")
                                .append(Character.forDigit((c >> 4) & 0xF, 16))
                                .append(Character.forDigit(c & 0xF, 16));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static int compareByCodePoint(String a, String b) {
        int ia = 0;
        int ib = 0;
        while (ia < a.length() && ib < b.length()) {
            int ca = a.codePointAt(ia);
            int cb = b.codePointAt(ib);
            if (ca != cb) {
                return Integer.compare(ca, cb);
            }
            ia += Character.charCount(ca);
            ib += Character.charCount(cb);
        }
        return Integer.compare(a.length() - ia, b.length() - ib);
    }
}
