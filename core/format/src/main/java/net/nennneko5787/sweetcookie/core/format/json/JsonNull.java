package net.nennneko5787.sweetcookie.core.format.json;

/**
 * JSON {@code null}.
 *
 * <p>Represented rather than mapped to a Java {@code null}, because SC-110 §2 forbids {@code null}
 * inside the IR and because "the key was written with an explicit null" and "the key was absent"
 * are different authoring mistakes that deserve different diagnostics.
 */
public record JsonNull() implements JsonValue {

    public static final JsonNull INSTANCE = new JsonNull();

    @Override
    public String typeName() {
        return "null";
    }

    @Override
    public String toString() {
        return "null";
    }
}
