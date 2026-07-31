package net.nennneko5787.sweetcookie.core.format.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import net.nennneko5787.sweetcookie.core.format.value.PackId;
import net.nennneko5787.sweetcookie.core.format.value.Provenance;
import org.junit.jupiter.api.Test;

/**
 * Diagnostics carry enough to be grouped and rendered. SC-240 §1, SC-280 §5.
 *
 * <p>The management screen groups by pack and quotes errors in full. Both of those are only possible
 * because a diagnostic knows where it came from and renders to something a human can read, so this
 * pins the two properties the UI depends on rather than assuming them.
 */
@ProvesSpec("SC-240")
class DiagnosticLogTest {

    private static final PackId WIZARDRY = PackId.derived("wizardry");

    @Test
    @ProvesSpec("SC-240")
    void aDiagnosticNamesThePackItCameFrom() {
        // What lets the add-on screen say "this pack is broken" rather than "something is broken".
        Diagnostic diagnostic = FormatDiagnostics.JSON_MALFORMED.at(
                Provenance.file(WIZARDRY, "entities/wizard.json"), "boom", 3, 7);

        assertEquals(WIZARDRY, diagnostic.where().orElseThrow().pack());
        assertEquals("entities/wizard.json", diagnostic.where().orElseThrow().path());
    }

    @Test
    @ProvesSpec("SC-240")
    void aDiagnosticWithNoPackIsDistinguishable() {
        // A corrupt archive is reported before any pack has an identity. The screen puts those in
        // their own section; if they were indistinguishable they would be silently dropped.
        Diagnostic diagnostic = FormatDiagnostics.JSON_MALFORMED.at(
                Provenance.file(PackId.NONE, "broken.mcaddon"), "boom", 1, 1);
        assertTrue(diagnostic.where().orElseThrow().pack().isNone());
    }

    @Test
    @ProvesSpec("SC-240")
    void rendersToOneReadableLineNamingTheCodeAndThePlace() {
        // Quoted verbatim under its pack on the screen, so it has to stand alone.
        String rendered = FormatDiagnostics.JSON_MALFORMED
                .at(Provenance.file(WIZARDRY, "entities/wizard.json"), "boom", 3, 7)
                .toString();

        assertTrue(rendered.startsWith("SCE-1032"), rendered);
        assertTrue(rendered.contains("entities/wizard.json"), rendered);
        assertTrue(rendered.lines().count() == 1, "a multi-line diagnostic breaks every row layout");
    }

    @Test
    @ProvesSpec("SC-240")
    void severityOrdersSoTheWorstOneCanBecomeABadge() {
        // The screen shows one badge per pack: the worst severity reported against it. That needs
        // an ordering, and it needs INFO to be the least of them.
        assertEquals(Severity.ERROR,
                List.of(Severity.INFO, Severity.ERROR, Severity.WARNING).stream()
                        .max(java.util.Comparator.naturalOrder()).orElseThrow());
        assertEquals(Severity.INFO,
                List.of(Severity.INFO).stream().max(java.util.Comparator.naturalOrder()).orElseThrow());
    }
}
