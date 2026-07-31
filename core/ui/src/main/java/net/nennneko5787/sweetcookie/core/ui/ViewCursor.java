package net.nennneko5787.sweetcookie.core.ui;

import java.util.List;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * Keyboard selection over a {@link ViewModel}'s actionable rows. SC-280 §5, §7.
 *
 * <p><b>Why the keyboard is the primary way to operate this screen.</b> Java Edition reorders packs
 * by dragging one list into another. A drag needs a mouse, needs both the pack and its destination
 * on screen at once, and reports nothing about where the pack ended up; with twenty packs installed
 * it is slow and error-prone, and it is unusable while holding a controller or on a trackpad. Here a
 * row is selected with the arrow keys and acted on with a single labelled key, the available keys
 * are drawn under the selection so nothing has to be memorised, and every action ends in a
 * confirmation naming the new position.
 *
 * <p>Minecraft-free on purpose, which is what SC-280 §7 asks for: this class is where the screen's
 * behaviour lives, so the behaviour can be asserted in a plain unit test rather than a screenshot.
 * It takes key codes as integers — GLFW's, which for every printable key <b>is</b> the ASCII code of
 * the character on that key, so a hotkey declared as {@code 'D'} needs no lookup table and no
 * dependency here.
 */
@SpecImpl("SC-280")
public final class ViewCursor {

    // GLFW's codes for the keys that have no character. Identical on both supported versions, and
    // in truth fixed by GLFW rather than by Minecraft.
    public static final int KEY_UP = 265;
    public static final int KEY_DOWN = 264;

    private final ViewModel view;
    private int selected;

    public ViewCursor(ViewModel view) {
        this.view = view;
    }

    /** The rows the cursor can land on, in display order. */
    public List<ViewModel.Row> rows() {
        return view.actionable();
    }

    /** Which row is selected, or empty when the view has nothing to act on. */
    public Optional<ViewModel.Row> selection() {
        List<ViewModel.Row> rows = rows();
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(clamp(selected)));
    }

    /** The selected row's index among {@link #rows()}, for a renderer to highlight. */
    public int selectedIndex() {
        return clamp(selected);
    }

    /**
     * Puts the selection back where it was, clamped to what now exists.
     *
     * <p>Used when the view is rebuilt after an action. Disabling the last enabled pack removes its
     * row, and a user whose selection jumped to the top has to find their place again in a list they
     * were halfway through.
     */
    public ViewCursor select(int index) {
        selected = index;
        return this;
    }

    /**
     * Handles a key.
     *
     * <p>Returns the command the key asked for, so that the caller — a {@code Screen}, the only part
     * of this that has to be written twice — does nothing but send it. Arrow keys move and return
     * empty; an unrecognised key returns empty and is reported as unhandled so the screen's own
     * bindings, Escape included, keep working.
     *
     * @return the command to run without its leading slash, or empty if the key did not ask for one
     */
    public Optional<String> press(int key) {
        List<ViewModel.Row> rows = rows();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (key == KEY_UP || key == KEY_DOWN) {
            // Wraps rather than stopping. The list is a ring of a dozen items on the same screen,
            // and stopping at the end costs a user the length of the list to get back to the top.
            selected = Math.floorMod(clamp(selected) + (key == KEY_DOWN ? 1 : -1), rows.size());
            return Optional.empty();
        }
        return rows.get(clamp(selected)).actions().stream()
                .filter(action -> matches(action.hotkey(), key))
                .findFirst()
                .map(ViewModel.Action::command);
    }

    /** True when a key does something here, so a screen can decline everything else. */
    public boolean handles(int key) {
        return key == KEY_UP || key == KEY_DOWN
                || selection().stream().flatMap(row -> row.actions().stream())
                        .anyMatch(action -> matches(action.hotkey(), key));
    }

    /**
     * Keeps the selection on a row that exists.
     *
     * <p>Clamped when read rather than when the view changes: a screen rebuilt after a pack was
     * disabled has one fewer row, and a selection that pointed at the last one must not throw before
     * anyone notices it moved.
     */
    private int clamp(int candidate) {
        int size = rows().size();
        return size == 0 ? 0 : Math.max(0, Math.min(candidate, size - 1));
    }

    /** GLFW reports letters as their uppercase character, so a lowercase hotkey still matches. */
    private static boolean matches(char hotkey, int key) {
        return Character.toUpperCase(hotkey) == key;
    }
}
