package net.nennneko5787.sweetcookie.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SC-280 §5.2, asserted without a mouse or a client.
 *
 * <p>Every case here is a real drag — press at a height, move to another, release — reading back the
 * commands that would have been sent. The section's drop behaviour is a stand-in for the one
 * {@code Views} builds, including the remove-then-reinsert arithmetic, which is the part that is
 * wrong by one in every implementation that has not been tested this way.
 */
class ViewDragTest {

    private final List<String> enabled = new ArrayList<>(List.of("alpha", "beta", "gamma"));
    private final List<String> disabled = new ArrayList<>(List.of("delta"));

    /** The same rules Views applies, kept here so the arithmetic is what is under test. */
    private ViewModel view() {
        return new ViewModel("packs", List.of(
                ViewModel.Section.of("enabled", rows(enabled), (dragged, position) -> {
                    int from = enabled.indexOf(dragged.key());
                    if (from < 0) {
                        return List.of("enable " + dragged.key(),
                                "order " + (Math.min(position, enabled.size()) + 1) + " "
                                        + dragged.key());
                    }
                    int target = position > from ? position - 1 : position;
                    return target == from
                            ? List.of()
                            : List.of("order " + (target + 1) + " " + dragged.key());
                }),
                ViewModel.Section.of("not enabled", rows(disabled), (dragged, position) ->
                        enabled.contains(dragged.key())
                                ? List.of("disable " + dragged.key())
                                : List.of())));
    }

    private static List<ViewModel.Row> rows(List<String> keys) {
        return keys.stream()
                .map(key -> ViewModel.Row.of(key, "1.0.0").keyed(key)
                        .with(List.of(new ViewModel.Action("disable", 'D', "disable " + key))))
                .toList();
    }

    /** The y at the middle of a row, as it is actually laid out. */
    private int middleOf(String key) {
        ViewModel view = view();
        for (ViewLayout.Region region : ViewLayout.regions(view, 0)) {
            if (region.row() == ViewLayout.SECTION_BODY) {
                continue;
            }
            if (view.sections().get(region.section()).rows().get(region.row()).key().equals(key)) {
                return (region.top() + region.bottom()) / 2;
            }
        }
        throw new IllegalArgumentException(key);
    }

    private int topThirdOf(String key) {
        ViewModel view = view();
        for (ViewLayout.Region region : ViewLayout.regions(view, 0)) {
            if (region.row() == ViewLayout.SECTION_BODY) {
                continue;
            }
            if (view.sections().get(region.section()).rows().get(region.row()).key().equals(key)) {
                return region.top() + 1;
            }
        }
        throw new IllegalArgumentException(key);
    }

    private List<String> dragFromTo(String key, int toY) {
        ViewDrag drag = new ViewDrag();
        assertTrue(drag.press(view(), 0, middleOf(key)), "nothing was picked up");
        drag.moveTo(toY);
        return drag.release(view(), 0);
    }

    @Test
    void draggingDownwardsAccountsForTheRowLeavingItsOwnPlace() {
        // alpha is at 0. Dropped below gamma the insertion index is 3, but order() removes alpha
        // first, so the target is 2. Off by one here moves the pack one place short of the
        // insertion mark, every single time, which reads as the screen ignoring the drop.
        assertEquals(List.of("order 3 alpha"), dragFromTo("alpha", middleOf("gamma") + 5));
    }

    @Test
    void draggingUpwardsUsesTheIndexAsGiven() {
        assertEquals(List.of("order 1 gamma"), dragFromTo("gamma", topThirdOf("alpha")));
    }

    @Test
    void droppingAPackWhereItAlreadyIsDoesNothing() {
        // A user who nudged the mouse must not be told they reordered the list.
        assertEquals(List.of(), dragFromTo("beta", middleOf("beta")));
    }

    @Test
    void draggingIntoTheEnabledSectionEnablesAndPositions() {
        assertEquals(List.of("enable delta", "order 1 delta"),
                dragFromTo("delta", topThirdOf("alpha")));
    }

    @Test
    void draggingOutOfTheEnabledSectionDisables() {
        assertEquals(List.of("disable beta"), dragFromTo("beta", middleOf("delta")));
    }

    @Test
    void draggingAnAlreadyDisabledPackWithinItsOwnSectionDoesNothing() {
        assertEquals(List.of(), dragFromTo("delta", middleOf("delta")));
    }

    @Test
    void aClickThatNeverMovedIsNotADrag() {
        // Otherwise every click is a one-pixel drag and selecting a row reorders the list.
        ViewDrag drag = new ViewDrag();
        assertTrue(drag.press(view(), 0, middleOf("alpha")));
        drag.moveTo(middleOf("alpha") + 2);
        assertFalse(drag.dragging());
        assertEquals(List.of(), drag.release(view(), 0));
    }

    @Test
    void aClickStillReportsWhatItGrabbedSoTheKeyboardCanFollowIt() {
        ViewDrag drag = new ViewDrag();
        drag.press(view(), 0, middleOf("beta"));
        assertEquals("beta", drag.heldKey().orElseThrow());
    }

    @Test
    void aRowThatCannotActCannotBePickedUp() {
        ViewModel view = new ViewModel("t", List.of(
                ViewModel.Section.of("s", List.of(ViewModel.Row.empty("no add-ons installed")))));
        ViewLayout.Region region = ViewLayout.regions(view, 0).stream()
                .filter(r -> r.row() != ViewLayout.SECTION_BODY).findFirst().orElseThrow();
        assertFalse(new ViewDrag().press(view, 0, (region.top() + region.bottom()) / 2.0));
    }

    @Test
    void releasingOverASectionThatTakesNoDropsDoesNothingAndLetsGo() {
        ViewModel view = new ViewModel("t", List.of(
                ViewModel.Section.of("draggable", rows(List.of("alpha"))),
                ViewModel.Section.of("inert", List.of(ViewModel.Row.empty("nothing here")))));
        ViewLayout.Region alpha = ViewLayout.regions(view, 0).stream()
                .filter(r -> r.row() == 0 && r.section() == 0).findFirst().orElseThrow();
        ViewDrag drag = new ViewDrag();
        drag.press(view, 0, (alpha.top() + alpha.bottom()) / 2.0);
        drag.moveTo(alpha.bottom() + 200);
        assertEquals(List.of(), drag.release(view, 0));
        assertFalse(drag.holding(), "a failed drop must still let go");
    }

    @Test
    void anEmptySectionThatTakesDropsIsStillAPlaceToDropInto() {
        // Otherwise the first pack can never be enabled by dragging, because there is no row in the
        // enabled list to aim at.
        enabled.clear();
        ViewDrag drag = new ViewDrag();
        int emptyEnabledBand = ViewLayout.regions(view(), 0).stream()
                .filter(region -> region.section() == 0).mapToInt(ViewLayout.Region::bottom)
                .max().orElseThrow() - 2;
        assertTrue(drag.press(view(), 0, middleOf("delta")));
        drag.moveTo(emptyEnabledBand);
        assertEquals(List.of("enable delta", "order 1 delta"), drag.release(view(), 0));
    }

    @Test
    void theInsertionMarkTracksThePointerWhileTheButtonIsDown() {
        // What Java Edition's screen does not answer until after you have let go.
        ViewDrag drag = new ViewDrag();
        drag.press(view(), 0, middleOf("alpha"));
        drag.moveTo(topThirdOf("gamma"));
        ViewLayout.Drop drop = drag.target(view(), 0).orElseThrow();
        assertEquals(0, drop.section());
        assertEquals(2, drop.position());
    }
}
