package net.nennneko5787.sweetcookie.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * SC-280 §7's behaviour contract, asserted without a Minecraft client.
 *
 * <p>These are the tests that a screen written against {@code Screen} could not have: they press
 * keys and read back the command that would have been sent, on both supported versions at once,
 * in milliseconds.
 */
class ViewCursorTest {

    private static ViewModel.Row pack(String name, ViewModel.Action... actions) {
        return ViewModel.Row.of(name, "1.0.0").with(List.of(actions));
    }

    private static ViewModel.Action action(String label, char key, String command) {
        return new ViewModel.Action(label, key, command);
    }

    private static ViewModel view() {
        return new ViewModel("packs", List.of(
                ViewModel.Section.of("enabled", List.of(
                        pack("alpha", action("disable", 'D', "sweetcookie disable alpha"),
                                action("raise priority", ']', "sweetcookie order 2 alpha")),
                        pack("beta", action("disable", 'D', "sweetcookie disable beta"),
                                action("lower priority", '[', "sweetcookie order 1 beta")))),
                ViewModel.Section.of("not enabled", List.of(
                        ViewModel.Row.empty("nothing to say"),
                        pack("gamma", action("enable", 'E', "sweetcookie enable gamma"))))));
    }

    @Test
    void skipsRowsThatCannotDoAnything() {
        // "nothing to say" sits between beta and gamma and is not selectable: stepping through
        // statements to reach the next pack is what makes a list tedious.
        assertEquals(List.of("alpha", "beta", "gamma"),
                new ViewCursor(view()).rows().stream().map(ViewModel.Row::label).toList());
    }

    @Test
    void arrowKeysMoveWithoutRunningAnything() {
        ViewCursor cursor = new ViewCursor(view());
        assertEquals(Optional.empty(), cursor.press(ViewCursor.KEY_DOWN));
        assertEquals("beta", cursor.selection().orElseThrow().label());
    }

    @Test
    void movingWrapsAtBothEnds() {
        ViewCursor cursor = new ViewCursor(view());
        cursor.press(ViewCursor.KEY_UP);
        assertEquals("gamma", cursor.selection().orElseThrow().label());
        cursor.press(ViewCursor.KEY_DOWN);
        assertEquals("alpha", cursor.selection().orElseThrow().label());
    }

    @Test
    void aHotkeyReturnsTheCommandForTheSelectedRow() {
        ViewCursor cursor = new ViewCursor(view());
        cursor.press(ViewCursor.KEY_DOWN);
        assertEquals(Optional.of("sweetcookie disable beta"), cursor.press('D'));
    }

    @Test
    void aHotkeyBelongingToAnotherRowDoesNothing() {
        // 'E' enables gamma. Pressed on alpha it must not enable gamma, which is the failure mode
        // of a screen that looks up hotkeys globally.
        ViewCursor cursor = new ViewCursor(view());
        assertEquals(Optional.empty(), cursor.press('E'));
    }

    @Test
    void lowercaseHotkeysMatchTheUppercaseKeyGlfwReports() {
        assertTrue(new ViewCursor(new ViewModel("t", List.of(ViewModel.Section.of("s", List.of(
                pack("alpha", action("do", 'd', "sweetcookie packs"))))))).press('D').isPresent());
    }

    @Test
    void unrecognisedKeysAreDeclinedSoTheScreenKeepsItsOwn() {
        // Escape (GLFW 256) must reach the screen, or the list cannot be closed.
        ViewCursor cursor = new ViewCursor(view());
        assertFalse(cursor.handles(256));
        assertEquals(Optional.empty(), cursor.press(256));
    }

    @Test
    void aViewWithNothingToActOnIsSafeToPress() {
        ViewCursor cursor = new ViewCursor(new ViewModel("empty", List.of(
                ViewModel.Section.of("installed", List.of(ViewModel.Row.empty("none"))))));
        assertEquals(Optional.empty(), cursor.selection());
        assertEquals(Optional.empty(), cursor.press(ViewCursor.KEY_DOWN));
        assertEquals(0, cursor.selectedIndex());
    }

    @Test
    void aSelectionRestoredPastTheEndLandsOnTheLastRow() {
        // What happens after disabling the last pack: the rebuilt view is shorter than the index
        // carried over from the old one.
        ViewCursor cursor = new ViewCursor(view()).select(99);
        assertEquals("gamma", cursor.selection().orElseThrow().label());
    }
}
