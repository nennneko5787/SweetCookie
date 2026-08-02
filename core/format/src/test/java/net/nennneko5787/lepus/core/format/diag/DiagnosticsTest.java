package net.nennneko5787.lepus.core.format.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.Provenance;
import org.junit.jupiter.api.Test;

/** The diagnostics collector, SC-240 §1–§3. */
@ProvesSpec("SC-240")
class DiagnosticsTest {

    private static final DiagnosticType UNKNOWN_COMPONENT =
            new DiagnosticType(3001, Severity.WARNING, "lepus.diagnostic.test.unknown");

    private static Provenance at(String path) {
        return Provenance.file(PackId.derived("wizardry"), path);
    }

    @Test
    @ProvesSpec("SC-240")
    void deduplicatesByCodeAndLocationAndCounts() {
        // A filter with an unknown test runs every tick, per entity. Without this a single unknown
        // construct produces a log nobody reads, which is the same as no log.
        Diagnostics diagnostics = new Diagnostics();
        for (int i = 0; i < 413; i++) {
            diagnostics.report(UNKNOWN_COMPONENT, at("entities/wizard.json"), "minecraft:nope");
        }

        DiagnosticLog log = diagnostics.snapshot();
        assertEquals(1, log.occurrences().size());
        assertEquals(413, log.occurrences().get(0).count());
    }

    @Test
    @ProvesSpec("SC-240")
    void keepsDifferentLocationsApart() {
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.report(UNKNOWN_COMPONENT, at("entities/a.json"));
        diagnostics.report(UNKNOWN_COMPONENT, at("entities/b.json"));
        assertEquals(2, diagnostics.snapshot().occurrences().size());
    }

    @Test
    @ProvesSpec("SC-240")
    void acceptsAFinerDeduplicationKeyWhereTheDefaultIsTooCoarse() {
        // One file naming three unknown components deserves three lines, not one.
        Diagnostics diagnostics = new Diagnostics();
        Provenance where = at("entities/wizard.json");
        for (String name : List.of("a", "b", "c", "a")) {
            diagnostics.report(UNKNOWN_COMPONENT.at(where, name), List.of(where, name));
        }
        assertEquals(3, diagnostics.snapshot().occurrences().size());
    }

    @Test
    @ProvesSpec("SC-240")
    void reportsInFirstReportOrder() {
        // SC-110 §10: everything about a load is deterministic, including what the user reads.
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.report(UNKNOWN_COMPONENT, at("z.json"));
        diagnostics.report(UNKNOWN_COMPONENT, at("a.json"));
        diagnostics.report(UNKNOWN_COMPONENT, at("z.json"));

        List<String> paths = diagnostics.snapshot().diagnostics().stream()
                .map(d -> d.where().orElseThrow().path())
                .toList();
        assertEquals(List.of("z.json", "a.json"), paths);
    }

    @Test
    @ProvesSpec("SC-240")
    void separatesErrorsFromEverythingElse() {
        Diagnostics diagnostics = new Diagnostics();
        assertFalse(diagnostics.hasErrors());

        diagnostics.report(UNKNOWN_COMPONENT, at("a.json"));
        assertFalse(diagnostics.hasErrors());

        diagnostics.report(FormatDiagnostics.JSON_MALFORMED.at(at("b.json"), "boom", 1, 1));
        assertTrue(diagnostics.hasErrors());
        assertTrue(diagnostics.snapshot().hasErrors());
        assertEquals(1, diagnostics.snapshot().at(Severity.ERROR).size());
        assertEquals(1, diagnostics.snapshot().at(Severity.WARNING).size());
    }

    @Test
    @ProvesSpec("SC-240")
    void snapshotsAreImmutableAndTheCollectorStaysUsable() {
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.report(UNKNOWN_COMPONENT, at("a.json"));
        DiagnosticLog first = diagnostics.snapshot();

        diagnostics.report(UNKNOWN_COMPONENT, at("b.json"));
        assertEquals(1, first.occurrences().size());
        assertEquals(2, diagnostics.snapshot().occurrences().size());
        assertThrows(UnsupportedOperationException.class, () -> first.occurrences().clear());
    }

    @Test
    @ProvesSpec("SC-240")
    void mergingConcatenatesWithoutCollapsingSeparatePacks() {
        // Two packs reporting the same code at different locations are two reports. A merge that
        // collapsed them would hide the second pack, which defeats the point of provenance.
        Diagnostics a = new Diagnostics();
        a.report(UNKNOWN_COMPONENT, at("a.json"));
        Diagnostics b = new Diagnostics();
        b.report(UNKNOWN_COMPONENT, at("b.json"));

        assertEquals(2, a.snapshot().merge(b.snapshot()).occurrences().size());
        assertEquals(1, a.snapshot().merge(DiagnosticLog.empty()).occurrences().size());
        assertTrue(DiagnosticLog.empty().isEmpty());
    }

    @Test
    @ProvesSpec("SC-240")
    void everyCodeIsAllocatedOnceAndCarriesAMessageKey() {
        // SC-240 §5 and §7. Codes are never reused and never renumbered, because users search the
        // internet for them — so the check is that the holder cannot contain a collision.
        Set<Integer> seen = new HashSet<>();
        for (DiagnosticType type : FormatDiagnostics.all()) {
            assertTrue(seen.add(type.code()), "SCE-" + type.code() + " is allocated twice");
            assertTrue(type.code() >= 1000 && type.code() <= 2999,
                    type.codeString() + " is outside the ranges core/format owns");
            assertFalse(type.messageKey().isBlank(), type.codeString() + " has no message key");
        }
        assertFalse(seen.isEmpty(), "the holder found no codes, so this check proves nothing");
    }

    @Test
    @ProvesSpec("SC-240")
    void refusesACodeOutsideTheAllocatedSpace() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiagnosticType(42, Severity.INFO, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new DiagnosticType(1000, Severity.INFO, "  "));
    }

    @Test
    @ProvesSpec("SC-240")
    void severityOrdersFromInfoUpwards() {
        assertTrue(Severity.ERROR.atLeast(Severity.WARNING));
        assertTrue(Severity.WARNING.atLeast(Severity.WARNING));
        assertFalse(Severity.INFO.atLeast(Severity.WARNING));
    }
}
