package net.nennneko5787.sweetcookie.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import net.nennneko5787.sweetcookie.core.format.diag.Severity;
import org.junit.jupiter.api.Test;

/** SC-280 §7: the widget description is asserted as a line list rather than as a screenshot. */
class ViewLayoutTest {

    private static ViewModel view(int packs) {
        return new ViewModel("SweetCookie add-ons", List.of(ViewModel.Section.of(
                "enabled in this world, lowest priority first (the last one wins)",
                Stream.iterate(0, i -> i + 1).limit(packs)
                        .map(i -> ViewModel.Row.of("pack " + i, "1.0.0 - 1 block").with(List.of(
                                new ViewModel.Action("disable", 'D', "sweetcookie disable p" + i))))
                        .toList())));
    }

    private static List<String> texts(List<ViewLayout.Line> lines) {
        return lines.stream().map(ViewLayout.Line::text).toList();
    }

    @Test
    void theSelectedRowIsMarkedAndTheOthersAreNot() {
        List<String> lines = texts(ViewLayout.lay(view(3), 0, 1));
        assertTrue(lines.contains("> pack 1"), lines.toString());
        assertTrue(lines.contains("  pack 0"), lines.toString());
        assertTrue(lines.contains("  pack 2"), lines.toString());
    }

    @Test
    void onlyTheSelectedRowPrintsItsKeys() {
        // The keys sit under the thing they act on rather than in a legend, so exactly one row shows
        // them. A legend has to be read and remembered; this is read where the eye already is.
        assertEquals(1, texts(ViewLayout.lay(view(3), 0, 1)).stream()
                .filter(line -> line.contains("[D] disable")).count());
    }

    @Test
    void aViewWithNothingSelectedPrintsNoKeysAtAll() {
        assertTrue(texts(ViewLayout.lay(view(3), 0, ViewLayout.NOTHING_SELECTED)).stream()
                .noneMatch(line -> line.contains("[D]")));
    }

    @Test
    void theSelectedRowIsDrawnInTheSelectedColour() {
        List<ViewLayout.Line> lines = ViewLayout.lay(view(2), 0, 0);
        int selected = lines.stream().filter(l -> l.text().equals("> pack 0"))
                .findFirst().orElseThrow().argb();
        int other = lines.stream().filter(l -> l.text().equals("  pack 1"))
                .findFirst().orElseThrow().argb();
        assertNotEquals(selected, other);
    }

    @Test
    void aSelectionBelowTheFoldScrollsIntoView() {
        // The bug this prevents: arrow keys walk the selection off the bottom edge and the screen
        // looks like it stopped responding.
        ViewModel view = view(40);
        int screenHeight = 200;
        int scroll = ViewLayout.scrollTo(view, 39, screenHeight, 0);
        int y = ViewLayout.lay(view, scroll, 39).stream()
                .filter(line -> line.text().startsWith("> ")).findFirst().orElseThrow().y();
        assertTrue(y >= 0 && y <= screenHeight, "selected row at y=" + y);
    }

    @Test
    void aSelectionAlreadyOnScreenDoesNotMoveTheList() {
        // Scrolling by the least it can: a selection moving one row must not throw the whole list
        // to a new position and cost the user their place.
        assertEquals(0, ViewLayout.scrollTo(view(40), 1, 400, 0));
    }

    @Test
    void aSelectionAboveTheFoldScrollsBackUp() {
        ViewModel view = view(40);
        assertTrue(ViewLayout.scrollTo(view, 0, 200, 500) < 500);
    }

    @Test
    void diagnosticsAreQuotedUnderTheirRowInTheErrorColour() {
        ViewModel view = new ViewModel("t", List.of(ViewModel.Section.of("s", List.of(
                ViewModel.Row.of("broken pack", "1.0.0", Severity.ERROR,
                        List.of("SCE-1001 manifest.json is not readable"))))));
        ViewLayout.Line note = ViewLayout.lay(view, 0).stream()
                .filter(line -> line.text().startsWith("SCE-1001")).findFirst().orElseThrow();
        assertEquals(0xFFFF6B6B, note.argb());
    }
}
