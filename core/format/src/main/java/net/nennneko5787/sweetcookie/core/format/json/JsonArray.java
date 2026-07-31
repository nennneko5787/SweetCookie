package net.nennneko5787.sweetcookie.core.format.json;

import java.util.List;
import java.util.Optional;

/**
 * A JSON array.
 *
 * @param values the elements, in order; the list is unmodifiable
 */
public record JsonArray(List<JsonValue> values) implements JsonValue {

    public static final JsonArray EMPTY = new JsonArray(List.of());

    public JsonArray {
        values = List.copyOf(values);
    }

    @Override
    public String typeName() {
        return "array";
    }

    public int size() {
        return values.size();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** Element {@code index}, or empty when out of range. Out of range is not an exception here. */
    public Optional<JsonValue> get(int index) {
        return index >= 0 && index < values.size()
                ? Optional.of(values.get(index))
                : Optional.empty();
    }

    /**
     * The elements as {@code float}s, skipping any that are not numbers.
     *
     * <p>For {@code [x, y, z]} shapes, which Bedrock uses everywhere. The caller checks the size:
     * silently accepting a two-element vector would produce a pivot at the origin with no
     * diagnostic, and that is exactly the kind of failure constitution rule 8 exists to prevent.
     */
    public List<Float> floats() {
        return values.stream().flatMap(v -> v.asNumber().stream()).map(JsonNumber::floatValue)
                .toList();
    }

    @Override
    public String toString() {
        return toCanonicalString();
    }
}
