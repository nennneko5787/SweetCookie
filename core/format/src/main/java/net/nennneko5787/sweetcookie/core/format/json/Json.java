package net.nennneko5787.sweetcookie.core.format.json;

import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.DiagnosticType;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostics;
import net.nennneko5787.sweetcookie.core.format.diag.FormatDiagnostics;
import net.nennneko5787.sweetcookie.core.format.value.Provenance;

/**
 * The entry point to the JSON facade: text in, {@link JsonValue} out. SC-110 §2.1.
 *
 * <p>Two shapes, and the choice between them is the whole of this class's design.
 *
 * <ul>
 *   <li>{@link #parse} throws. Use it in tests, and wherever the input is ours rather than an
 *       add-on author's.
 *   <li>{@link #tryParse} reports a diagnostic and returns empty. Use it for anything that came out
 *       of an add-on — SC-000 §10 requires a structurally broken file to be skipped while the rest
 *       of the pack loads, and that is only implementable if the failure arrives as a value.
 * </ul>
 */
@SpecImpl("SC-110")
public final class Json {

    private Json() {
    }

    /**
     * Parses with the default limits.
     *
     * @throws JsonParseException if the text is not readable as Bedrock-dialect JSON
     */
    public static JsonValue parse(String text) {
        return parse(text, JsonLimits.DEFAULT);
    }

    /**
     * Parses with explicit limits.
     *
     * @throws JsonParseException if the text is not readable as Bedrock-dialect JSON
     */
    public static JsonValue parse(String text, JsonLimits limits) {
        return new JsonReader(text, limits).readDocument();
    }

    /**
     * Parses, or reports the failure against {@code where} and returns empty.
     *
     * <p>The reported code distinguishes the three failure kinds, because they need different
     * advice: malformed JSON means "fix the file", a duplicate key means "you have two of these and
     * only one was ever going to take effect", and excessive nesting means "we will not read this".
     */
    public static Optional<JsonValue> tryParse(String text, Provenance where, Diagnostics into) {
        return tryParse(text, JsonLimits.DEFAULT, where, into);
    }

    /** As {@link #tryParse(String, Provenance, Diagnostics)}, with explicit limits. */
    public static Optional<JsonValue> tryParse(
            String text, JsonLimits limits, Provenance where, Diagnostics into) {
        try {
            return Optional.of(parse(text, limits));
        } catch (JsonParseException e) {
            into.report(typeFor(e.kind()).at(where, e.getMessage(), e.line(), e.column()));
            return Optional.empty();
        }
    }

    /**
     * As {@link #tryParse}, additionally requiring the top-level value to be an object.
     *
     * <p>Almost every Bedrock file is one, and the exceptions name themselves. A top-level array
     * where an object was expected is reported rather than silently treated as an empty object,
     * which would degrade into "the pack does nothing and says nothing".
     */
    public static Optional<JsonObject> tryParseObject(
            String text, Provenance where, Diagnostics into) {
        Optional<JsonValue> root = tryParse(text, where, into);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        Optional<JsonObject> object = root.get().asObject();
        if (object.isEmpty()) {
            into.report(FormatDiagnostics.JSON_MALFORMED.at(
                    where,
                    "the top-level value is a " + root.get().typeName() + ", not an object",
                    1,
                    1));
        }
        return object;
    }

    private static DiagnosticType typeFor(JsonParseException.Kind kind) {
        return switch (kind) {
            case MALFORMED -> FormatDiagnostics.JSON_MALFORMED;
            case DUPLICATE_KEY -> FormatDiagnostics.JSON_DUPLICATE_KEY;
            case TOO_DEEP -> FormatDiagnostics.JSON_TOO_DEEP;
        };
    }
}
