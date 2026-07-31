package net.nennneko5787.sweetcookie.core.format.json;

/** A JSON boolean. */
public record JsonBool(boolean value) implements JsonValue {

    public static final JsonBool TRUE = new JsonBool(true);
    public static final JsonBool FALSE = new JsonBool(false);

    public static JsonBool of(boolean value) {
        return value ? TRUE : FALSE;
    }

    @Override
    public String typeName() {
        return "boolean";
    }

    @Override
    public String toString() {
        return Boolean.toString(value);
    }
}
