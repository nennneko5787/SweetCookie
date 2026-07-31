package net.nennneko5787.sweetcookie.core.format.json;

import java.util.Objects;

/** A JSON string, already unescaped. */
public record JsonString(String value) implements JsonValue {

    public JsonString {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String typeName() {
        return "string";
    }

    @Override
    public String toString() {
        return toCanonicalString();
    }
}
