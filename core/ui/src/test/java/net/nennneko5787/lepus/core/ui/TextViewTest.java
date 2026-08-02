package net.nennneko5787.lepus.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.nennneko5787.lepus.core.format.diag.Severity;
import org.junit.jupiter.api.Test;

/** The text backend — what a headless server's operator actually reads. SC-280 §3.1. */
class TextViewTest {

    @Test
    void actionsArePrintedAsTheCommandsThatDoThem() {
        // A dedicated server has no screen at all - selection is a client screen (SC-280 5.2).
        // A list that says reordering exists without saying how has told an operator nothing.
        ViewModel view = new ViewModel("packs", List.of(ViewModel.Section.of("enabled", List.of(
                ViewModel.Row.of("wizardry", "1.2.0 - 3 blocks").with(List.of(
                        new ViewModel.Action("disable", "lepus disable wizardry"),
                        new ViewModel.Action("lower priority",
                                "lepus order 1 wizardry")))))));
        assertTrue(TextView.render(view).contains(
                "        /lepus disable wizardry  /lepus order 1 wizardry"),
                TextView.render(view).toString());
    }

    @Test
    void aRowWithNoActionsPrintsNoCommandLine() {
        ViewModel view = new ViewModel("pool", List.of(ViewModel.Section.of("ledger", List.of(
                ViewModel.Row.of("lepus:wizardry.magic_block", "block_16/0037, 4 states")))));
        assertEquals(3, TextView.render(view).size());
    }

    @Test
    void severityIsMarkedInTextBecauseAConsoleHasNoColour() {
        ViewModel view = new ViewModel("packs", List.of(ViewModel.Section.of("installed", List.of(
                ViewModel.Row.of("broken", "0.0.1", Severity.ERROR,
                        List.of("SCE-1003 manifest.json: no modules"))))));
        List<String> lines = TextView.render(view);
        assertTrue(lines.get(2).startsWith("    [!] broken"), lines.toString());
        assertEquals("        SCE-1003 manifest.json: no modules", lines.get(3));
    }
}
