package net.nennneko5787.sweetcookie.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import net.nennneko5787.sweetcookie.core.format.diag.Severity;
import org.junit.jupiter.api.Test;

/** SC-280 §7.2: the widget description is asserted as a line list rather than as a screenshot. */
class ViewLayoutTest {

    private static ViewModel view(int rows) {
        return new ViewModel("SweetCookie block pool", List.of(ViewModel.Section.of(
                "ledger",
                Stream.iterate(0, i -> i + 1).limit(rows)
                        .map(i -> ViewModel.Row.of("pack " + i, "1.0.0 - 1 block"))
                        .toList())));
    }

    private static List<String> texts(List<ViewLayout.Line> lines) {
        return lines.stream().map(ViewLayout.Line::text).toList();
    }

    @Test
    void everyRowIsDrawnUnderItsHeading() {
        List<String> lines = texts(ViewLayout.lay(view(3), 0));
        assertEquals(List.of("SweetCookie block pool", "ledger",
                "pack 0", "1.0.0 - 1 block", "pack 1", "1.0.0 - 1 block",
                "pack 2", "1.0.0 - 1 block"), lines);
    }

    @Test
    void anEmptySectionIsSkippedRatherThanDrawnAsABareHeading() {
        ViewModel view = new ViewModel("t", List.of(
                ViewModel.Section.of("nothing here", List.of()),
                ViewModel.Section.of("something", List.of(ViewModel.Row.of("a", "b")))));
        assertTrue(texts(ViewLayout.lay(view, 0)).stream().noneMatch("nothing here"::equals));
    }

    @Test
    void scrollingMovesEveryLineByTheSameAmount() {
        List<ViewLayout.Line> unscrolled = ViewLayout.lay(view(4), 0);
        List<ViewLayout.Line> scrolled = ViewLayout.lay(view(4), 30);
        for (int i = 0; i < unscrolled.size(); i++) {
            assertEquals(unscrolled.get(i).y() - 30, scrolled.get(i).y());
        }
    }

    @Test
    void heightCoversTheLastLineSoABackendCanBoundItsScrolling() {
        List<ViewLayout.Line> lines = ViewLayout.lay(view(6), 0);
        assertTrue(ViewLayout.height(view(6)) > lines.get(lines.size() - 1).y());
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

    @Test
    void aRowsBadgeColoursItsLabel() {
        ViewModel view = new ViewModel("t", List.of(ViewModel.Section.of("s", List.of(
                ViewModel.Row.of("warned", "1.0.0", Severity.WARNING, List.of()),
                ViewModel.Row.of("plain", "1.0.0")))));
        List<ViewLayout.Line> lines = ViewLayout.lay(view, 0);
        int warned = lines.stream().filter(l -> l.text().equals("warned"))
                .findFirst().orElseThrow().argb();
        int plain = lines.stream().filter(l -> l.text().equals("plain"))
                .findFirst().orElseThrow().argb();
        assertEquals(0xFFFFC66B, warned);
        assertEquals(0xFFDDDDDD, plain);
    }
}
