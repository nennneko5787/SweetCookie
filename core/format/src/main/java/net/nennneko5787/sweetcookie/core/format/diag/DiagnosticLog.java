package net.nennneko5787.sweetcookie.core.format.diag;

import java.util.ArrayList;
import java.util.List;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * The immutable result of a load: every distinct diagnostic, in the order it was first reported,
 * each with how many times it occurred. SC-240 §3.
 *
 * <p>The occurrence count is why this is not a bare {@code List<Diagnostic>}. Deduplication is
 * mandatory on hot paths, and a deduplicated list that does not say "and 412 more" turns a
 * pack-wide problem into what looks like a one-off.
 *
 * @param occurrences distinct diagnostics with their counts, in first-report order
 */
@SpecImpl("SC-240")
public record DiagnosticLog(List<Occurrence> occurrences) {

    private static final DiagnosticLog EMPTY = new DiagnosticLog(List.of());

    /** One distinct diagnostic and how many times its deduplication key was reported. */
    public record Occurrence(Diagnostic diagnostic, int count) {
        public Occurrence {
            if (count < 1) {
                throw new IllegalArgumentException("an occurrence count is at least 1: " + count);
            }
        }
    }

    public DiagnosticLog {
        occurrences = List.copyOf(occurrences);
    }

    public static DiagnosticLog empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return occurrences.isEmpty();
    }

    /** The distinct diagnostics, without their counts. */
    public List<Diagnostic> diagnostics() {
        return occurrences.stream().map(Occurrence::diagnostic).toList();
    }

    /** Distinct diagnostics at exactly {@code severity}. */
    public List<Diagnostic> at(Severity severity) {
        return occurrences.stream()
                .map(Occurrence::diagnostic)
                .filter(d -> d.severity() == severity)
                .toList();
    }

    /** Distinct diagnostics carrying {@code code}, whatever their location. */
    public List<Diagnostic> withCode(int code) {
        return occurrences.stream()
                .map(Occurrence::diagnostic)
                .filter(d -> d.code() == code)
                .toList();
    }

    public boolean hasErrors() {
        return occurrences.stream().anyMatch(o -> o.diagnostic().severity() == Severity.ERROR);
    }

    /**
     * Concatenation, preserving order and keeping counts separate.
     *
     * <p>Deliberately not re-deduplicating across logs: two packs reporting the same code at
     * different locations are different reports, and a merged count that hid the second pack would
     * defeat the point of provenance.
     */
    public DiagnosticLog merge(DiagnosticLog other) {
        if (isEmpty()) {
            return other;
        }
        if (other.isEmpty()) {
            return this;
        }
        List<Occurrence> merged = new ArrayList<>(occurrences);
        merged.addAll(other.occurrences);
        return new DiagnosticLog(merged);
    }
}
