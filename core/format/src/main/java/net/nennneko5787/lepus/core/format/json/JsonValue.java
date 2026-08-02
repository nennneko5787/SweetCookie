package net.nennneko5787.lepus.core.format.json;

import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * The JSON facade. SC-110 §2.1.
 *
 * <p>{@code core/format} does not expose Gson or Jackson types in its API, for three reasons:
 * whether Gson is on the Minecraft classpath is a per-version accident, {@code core/} must be usable
 * with no Minecraft at all, and a facade makes the backend swappable when a faster parser is worth
 * having. The cost is one adapter per backend, which is small. There is currently one backend —
 * {@link Json}'s own reader — because Bedrock JSON is not strict JSON and every off-the-shelf parser
 * needed configuring into leniency anyway.
 *
 * <p>Sealed, so that a {@code switch} over the six shapes is exhaustive and adding a seventh breaks
 * every site that must handle it.
 */
@SpecImpl("SC-110")
public sealed interface JsonValue
        permits JsonObject, JsonArray, JsonString, JsonNumber, JsonBool, JsonNull {

    /** The JSON type name, for diagnostics: {@code object}, {@code array}, {@code string}… */
    String typeName();

    default Optional<JsonObject> asObject() {
        return this instanceof JsonObject o ? Optional.of(o) : Optional.empty();
    }

    default Optional<JsonArray> asArray() {
        return this instanceof JsonArray a ? Optional.of(a) : Optional.empty();
    }

    default Optional<String> asString() {
        return this instanceof JsonString s ? Optional.of(s.value()) : Optional.empty();
    }

    default Optional<JsonNumber> asNumber() {
        return this instanceof JsonNumber n ? Optional.of(n) : Optional.empty();
    }

    default Optional<Boolean> asBool() {
        return this instanceof JsonBool b ? Optional.of(b.value()) : Optional.empty();
    }

    default boolean isNull() {
        return this instanceof JsonNull;
    }

    /** This value as canonical JSON (SC-000 §6). Suitable for hashing and golden comparison. */
    default String toCanonicalString() {
        return CanonicalJson.write(this);
    }
}
