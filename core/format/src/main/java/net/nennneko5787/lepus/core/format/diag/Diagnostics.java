package net.nennneko5787.lepus.core.format.diag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.value.Provenance;

/**
 * The mutable collector diagnostics are reported into during a load. SC-240 §3.
 *
 * <p>Deduplication happens here, on report, not on read: the point is to stop a per-tick, per-entity
 * site from allocating a million records, and a collector that stored them all and filtered later
 * would not stop that.
 *
 * <p>{@code core/} returns diagnostics and never logs them (SC-000 §10). Nothing in this package has
 * a logging dependency, and adding one would make {@code core/} untestable in the way that matters —
 * a test asserts on the returned {@link DiagnosticLog}, not on captured log output.
 *
 * <p>Synchronised, because pack parsing is expected to become parallel and the contention here is
 * nil compared with JSON parsing.
 */
@SpecImpl("SC-240")
public final class Diagnostics {

    private final Map<Object, Slot> slots = new LinkedHashMap<>();

    private static final class Slot {
        final Diagnostic first;
        int count = 1;

        Slot(Diagnostic first) {
            this.first = first;
        }
    }

    /** Reports {@code diagnostic}, deduplicating on {@link Diagnostic#dedupKey()}. */
    public synchronized void report(Diagnostic diagnostic) {
        report(diagnostic, diagnostic.dedupKey());
    }

    /**
     * Reports {@code diagnostic} under an explicit deduplication key.
     *
     * <p>Use this where the default key — code plus location — is too coarse. The canonical case is
     * a single file naming many unknown components: the location is one file, but the useful report
     * is one line per component name, so the key includes the name.
     */
    public synchronized void report(Diagnostic diagnostic, Object dedupKey) {
        Slot slot = slots.get(dedupKey);
        if (slot == null) {
            slots.put(dedupKey, new Slot(diagnostic));
        } else {
            slot.count++;
        }
    }

    /** Convenience for the overwhelmingly common shape: a declared type, a location, arguments. */
    public void report(DiagnosticType type, Provenance where, Object... args) {
        report(type.at(where, args));
    }

    /** True when anything at {@link Severity#ERROR} has been reported. */
    public synchronized boolean hasErrors() {
        return slots.values().stream().anyMatch(s -> s.first.severity() == Severity.ERROR);
    }

    /** Distinct diagnostics reported so far. */
    public synchronized int distinctCount() {
        return slots.size();
    }

    /** An immutable snapshot. The collector remains usable afterwards. */
    public synchronized DiagnosticLog snapshot() {
        List<DiagnosticLog.Occurrence> out = slots.values().stream()
                .map(s -> new DiagnosticLog.Occurrence(s.first, s.count))
                .toList();
        return new DiagnosticLog(out);
    }
}
