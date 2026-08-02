package net.nennneko5787.lepus.core.format.diag;

import java.util.List;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.value.Provenance;

/**
 * The declaration of one {@code SCE-nnnn} code: its number, severity and message key, in one place.
 *
 * <p>SC-240 §5 requires that a code is allocated exactly once and never reused or renumbered. That
 * is only checkable if allocation happens somewhere a test can enumerate, so codes are declared as
 * constants of a {@code Diag} holder per specification document rather than written inline at the
 * emitting site.
 *
 * @param code       the {@code SCE-nnnn} number, without the prefix
 * @param severity   the severity every instance carries
 * @param messageKey the translation key every instance carries
 */
@SpecImpl("SC-240")
public record DiagnosticType(int code, Severity severity, String messageKey) {

    public DiagnosticType {
        if (code < 1000 || code > 9999) {
            throw new IllegalArgumentException("diagnostic code out of range: " + code);
        }
        if (messageKey.isBlank()) {
            throw new IllegalArgumentException("SCE-" + code + " has no message key");
        }
    }

    /** An instance with no location. Use only where no pack, file or pointer exists. */
    public Diagnostic of(Object... args) {
        return new Diagnostic(
                code, severity, messageKey, List.of(args), Optional.empty(), Optional.empty());
    }

    /** An instance located at {@code where}. Prefer this: a diagnostic with no location is noise. */
    public Diagnostic at(Provenance where, Object... args) {
        return new Diagnostic(
                code, severity, messageKey, List.of(args), Optional.of(where), Optional.empty());
    }

    /** An instance located at {@code where} and attributed to a Bedrock feature. */
    public Diagnostic feature(Provenance where, String featureId, Object... args) {
        return new Diagnostic(
                code,
                severity,
                messageKey,
                List.of(args),
                Optional.of(where),
                Optional.of(featureId));
    }

    /** The user-facing code, e.g. {@code SCE-1032}. */
    public String codeString() {
        return "SCE-" + code;
    }
}
