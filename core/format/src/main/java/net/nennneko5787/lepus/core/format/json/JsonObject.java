package net.nennneko5787.lepus.core.format.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A JSON object, preserving the order its members were written in.
 *
 * <p>Insertion order matters (SC-110 §10): identifier-collision tiebreaks and the on-disk block
 * ledger depend on a deterministic walk of the IR, and a {@code HashMap} here would make the ledger
 * non-deterministic — which corrupts worlds rather than merely producing an odd diff.
 *
 * <p>Keys are case-sensitive. VFS <em>paths</em> are case-insensitive (SC-100 §3); JSON keys are
 * not, and conflating the two would make {@code minecraft:Geometry} silently resolve.
 *
 * @param members the members, in insertion order; the map is unmodifiable
 */
public record JsonObject(Map<String, JsonValue> members) implements JsonValue {

    public static final JsonObject EMPTY = new JsonObject(Map.of());

    public JsonObject {
        members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
    }

    @Override
    public String typeName() {
        return "object";
    }

    public Optional<JsonValue> get(String key) {
        return Optional.ofNullable(members.get(key));
    }

    public boolean has(String key) {
        return members.containsKey(key);
    }

    /** Member names, in insertion order. */
    public Set<String> keys() {
        return members.keySet();
    }

    public int size() {
        return members.size();
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    // Typed accessors. Each returns empty both when the member is absent and when it is of the
    // wrong type: SC-000 §10 requires a malformed value for a known key to be dropped rather than
    // to abort the object, and forcing every call site to distinguish "absent" from "wrong type"
    // just to re-converge on the same behaviour would be noise. A site that must tell them apart
    // uses get() and reports its own diagnostic.

    public Optional<JsonObject> getObject(String key) {
        return get(key).flatMap(JsonValue::asObject);
    }

    public Optional<JsonArray> getArray(String key) {
        return get(key).flatMap(JsonValue::asArray);
    }

    public Optional<String> getString(String key) {
        return get(key).flatMap(JsonValue::asString);
    }

    public Optional<JsonNumber> getNumber(String key) {
        return get(key).flatMap(JsonValue::asNumber);
    }

    /** SC-000 §7: Bedrock numeric fields are {@code float}. */
    public Optional<Float> getFloat(String key) {
        return getNumber(key).map(JsonNumber::floatValue);
    }

    public Optional<Boolean> getBool(String key) {
        return get(key).flatMap(JsonValue::asBool);
    }

    @Override
    public String toString() {
        return toCanonicalString();
    }
}
