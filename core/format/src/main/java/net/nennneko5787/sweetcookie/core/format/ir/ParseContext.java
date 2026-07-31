package net.nennneko5787.sweetcookie.core.format.ir;

import java.util.List;
import java.util.Objects;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.DiagnosticType;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostics;
import net.nennneko5787.sweetcookie.core.format.json.JsonArray;
import net.nennneko5787.sweetcookie.core.format.json.JsonNumber;
import net.nennneko5787.sweetcookie.core.format.json.JsonObject;
import net.nennneko5787.sweetcookie.core.format.json.JsonPointer;
import net.nennneko5787.sweetcookie.core.format.json.JsonValue;
import net.nennneko5787.sweetcookie.core.format.value.BedrockVersion;
import net.nennneko5787.sweetcookie.core.format.value.Provenance;
import net.nennneko5787.sweetcookie.core.format.value.Vec2f;
import net.nennneko5787.sweetcookie.core.format.value.Vec3f;

/**
 * The cursor a parser carries as it descends: where it is, which version it is parsing as, and where
 * to report. SC-110 §3, §4.
 *
 * <p>Immutable, and {@link #at} returns a new one. That is what makes provenance cost a few bytes per
 * described node instead of a string per node: the pointer is extended by one token at each step
 * rather than rebuilt from the root, and a node that nothing ever reports against never allocates.
 *
 * <p>The accessor helpers ({@link #floats}, {@link #vec3}, …) all follow SC-000 §10: a malformed
 * value for a known key is reported, dropped, and parsing continues with the rest of the object.
 * None of them throws. A parser that wants to refuse a whole object checks for itself and returns
 * empty.
 */
@SpecImpl("SC-110")
public final class ParseContext {

    private final Provenance provenance;
    private final Diagnostics diagnostics;
    private final BedrockVersion effectiveVersion;

    public ParseContext(
            Provenance provenance, Diagnostics diagnostics, BedrockVersion effectiveVersion) {
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.effectiveVersion = Objects.requireNonNull(effectiveVersion, "effectiveVersion");
    }

    public Provenance provenance() {
        return provenance;
    }

    public Diagnostics diagnostics() {
        return diagnostics;
    }

    /**
     * The version this file is being parsed <b>as</b>, which is not necessarily what it declared.
     *
     * <p>Available for a parser that needs to know which of its own ladders to apply. It is
     * <b>not</b> a behavioural switch for the IR: SC-110 §3.2 is explicit that if downstream code
     * needs the origin version, that is a design failure to report rather than a field to read.
     */
    public BedrockVersion effectiveVersion() {
        return effectiveVersion;
    }

    /** A context at an object member. */
    public ParseContext at(String key) {
        return new ParseContext(
                provenance.at(JsonPointer.child(provenance.jsonPointer(), key)),
                diagnostics,
                effectiveVersion);
    }

    /** A context at an array element. */
    public ParseContext at(int index) {
        return new ParseContext(
                provenance.at(JsonPointer.index(provenance.jsonPointer(), index)),
                diagnostics,
                effectiveVersion);
    }

    /** Marks this position as having lost information, per SC-110 §3.2. */
    public ParseContext lossy() {
        return new ParseContext(provenance.markLossy(), diagnostics, effectiveVersion);
    }

    public void report(DiagnosticType type, Object... args) {
        diagnostics.report(type.at(provenance, args));
    }

    /**
     * Reports under an explicit deduplication key — for a site that fires per element.
     *
     * <p>Not an overload of {@link #report}: {@code report(type, key, args...)} and
     * {@code report(type, args...)} are ambiguous to the compiler for every call with two or more
     * trailing arguments, and an overload pair whose selection depends on argument count is one a
     * reader gets wrong even when the compiler does not.
     */
    public void reportPer(DiagnosticType type, Object dedupKey, Object... args) {
        diagnostics.report(type.at(provenance, args), List.of(provenance, dedupKey));
    }

    // ── Field accessors ──────────────────────────────────────────────────────────────────────

    /** A float, or {@code fallback} with a diagnostic when the member is present but not a number. */
    public float floatValue(JsonObject object, String key, float fallback) {
        return object.get(key)
                .map(value -> value.asNumber().map(JsonNumber::floatValue).orElseGet(() -> {
                    at(key).report(IrDiagnostics.FIELD_MALFORMED, key, value.typeName());
                    return fallback;
                }))
                .orElse(fallback);
    }

    public int intValue(JsonObject object, String key, int fallback) {
        return object.get(key)
                .map(value -> value.asNumber().map(JsonNumber::intValue).orElseGet(() -> {
                    at(key).report(IrDiagnostics.FIELD_MALFORMED, key, value.typeName());
                    return fallback;
                }))
                .orElse(fallback);
    }

    public boolean boolValue(JsonObject object, String key, boolean fallback) {
        return object.get(key)
                .map(value -> value.asBool().orElseGet(() -> {
                    at(key).report(IrDiagnostics.FIELD_MALFORMED, key, value.typeName());
                    return fallback;
                }))
                .orElse(fallback);
    }

    public String stringValue(JsonObject object, String key, String fallback) {
        return object.get(key)
                .map(value -> value.asString().orElseGet(() -> {
                    at(key).report(IrDiagnostics.FIELD_MALFORMED, key, value.typeName());
                    return fallback;
                }))
                .orElse(fallback);
    }

    /** The numbers in an array member, skipping non-numeric elements with one diagnostic. */
    public List<Float> floats(JsonObject object, String key) {
        JsonArray array = object.getArray(key).orElse(null);
        if (array == null) {
            object.get(key).ifPresent(value ->
                    at(key).report(IrDiagnostics.FIELD_MALFORMED, key, value.typeName()));
            return List.of();
        }
        List<Float> out = array.floats();
        if (out.size() != array.size()) {
            at(key).report(IrDiagnostics.FIELD_MALFORMED, key, "non-numeric element");
        }
        return out;
    }

    /**
     * A vector member.
     *
     * <p>A vector of the wrong length is reported and then read leniently, because a pack with a
     * two-element pivot has one broken bone and forty working ones, and refusing the file would cost
     * the forty.
     */
    public Vec3f vec3(JsonObject object, String key, Vec3f fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        List<Float> parts = floats(object, key);
        if (parts.isEmpty()) {
            return fallback;
        }
        if (parts.size() != 3) {
            at(key).report(IrDiagnostics.FIELD_MALFORMED, key, parts.size() + " components");
        }
        return Vec3f.of(parts);
    }

    public Vec2f vec2(JsonObject object, String key, Vec2f fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        List<Float> parts = floats(object, key);
        if (parts.isEmpty()) {
            return fallback;
        }
        if (parts.size() != 2) {
            at(key).report(IrDiagnostics.FIELD_MALFORMED, key, parts.size() + " components");
        }
        return Vec2f.of(parts);
    }

    /** Reports that a required member is absent, so the caller can skip the object. */
    public void reportMissing(String key) {
        report(IrDiagnostics.FIELD_REQUIRED, key);
    }

    /** Convenience for the shape every parser ends with. */
    public UnknownData unknown(JsonValue value, java.util.Set<String> recognised) {
        return value.asObject().map(o -> UnknownData.of(o, recognised)).orElse(UnknownData.EMPTY);
    }
}
