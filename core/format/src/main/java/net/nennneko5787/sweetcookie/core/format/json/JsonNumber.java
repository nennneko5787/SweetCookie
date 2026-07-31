package net.nennneko5787.sweetcookie.core.format.json;

import java.util.Objects;

/**
 * A JSON number, keeping both the literal as written and its numeric value.
 *
 * <p>The literal is kept because a round-trip test that re-serialised from the value alone could not
 * tell a parser that dropped precision from one that did not (SC-110 §5). The value is what every
 * consumer actually reads.
 *
 * <p>Equality is numeric: {@code 1}, {@code 1.0} and {@code 1e0} are the same JSON number, and
 * canonical JSON (SC-000 §6.4) writes all three identically. A test that cares about the literal
 * compares {@link #literal()}.
 *
 * <p>Not a record, because it caches the parsed value and defines equality over one component only.
 */
public final class JsonNumber implements JsonValue {

    private final String literal;
    private final double value;

    private JsonNumber(String literal, double value) {
        this.literal = literal;
        this.value = value;
    }

    /**
     * Wraps a literal as written in a file.
     *
     * @throws IllegalArgumentException if it is not parseable as a number
     */
    public static JsonNumber ofLiteral(String literal) {
        Objects.requireNonNull(literal, "literal");
        double parsed;
        try {
            parsed = Double.parseDouble(literal);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a JSON number: " + literal, e);
        }
        if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
            // Double.parseDouble accepts "NaN" and "Infinity"; JSON does not, and neither does
            // Bedrock. Accepting them here would produce a value no canonical writer can emit.
            throw new IllegalArgumentException("not a JSON number: " + literal);
        }
        return new JsonNumber(literal, parsed);
    }

    public static JsonNumber of(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("not representable in JSON: " + value);
        }
        return new JsonNumber(CanonicalJson.number(value), value);
    }

    public static JsonNumber of(long value) {
        return new JsonNumber(Long.toString(value), value);
    }

    /** The literal exactly as it appeared in the source. */
    public String literal() {
        return literal;
    }

    public double doubleValue() {
        return value;
    }

    /** SC-000 §7: Bedrock is float-typed, and widening "for accuracy" changes which branch a pack takes. */
    public float floatValue() {
        return (float) value;
    }

    /** Truncated toward zero, matching Bedrock. Saturates rather than wrapping. */
    public int intValue() {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    /** Truncated toward zero, matching Bedrock. Saturates rather than wrapping. */
    public long longValue() {
        return (long) value;
    }

    /** True when the value has no fractional part, whatever the literal looked like. */
    public boolean isIntegral() {
        return value == Math.rint(value) && !Double.isInfinite(value);
    }

    @Override
    public String typeName() {
        return "number";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JsonNumber other && Double.compare(value, other.value) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(value);
    }

    @Override
    public String toString() {
        return CanonicalJson.number(value);
    }
}
