package net.nennneko5787.sweetcookie.core.ui;

import java.util.List;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * Picking a row up, moving it, and dropping it somewhere. SC-280 §5.2, §7.1.
 *
 * <p>Dragging is how a pack list is reordered — it is what Java Edition does and what anyone opening
 * this screen will reach for. What it does <b>not</b> have to inherit is Java Edition's silence:
 * that screen tells you nothing about where a pack will land until you have let go, and nothing at
 * all about which end of the order wins. Here the insertion mark shows the landing point while the
 * button is still down, dropping into the other section enables or disables rather than requiring a
 * separate control, and the command that results says where the pack ended up.
 *
 * <p>Minecraft-free, so SC-280 §7's contract is a plain unit test: press, move, release, and assert
 * the commands that would have been sent — on both supported versions at once.
 *
 * <p>Holds a row's {@link ViewModel.Row#key key} rather than the row, because the view is rebuilt
 * every client tick underneath the drag. A held reference would be a stale object within 50 ms; a
 * key still names the same pack.
 */
@SpecImpl("SC-280")
public final class ViewDrag {

    /**
     * How far the pointer must move before a press becomes a drag.
     *
     * <p>Without it every click is a one-pixel drag, and a user who meant to select a row reorders
     * the list instead. The mouse moves during a click; this is the amount it is allowed to.
     */
    private static final double SLOP = 4.0;

    private String heldKey;
    private double pressedY;
    private double pointerY;
    private boolean moved;

    /**
     * Picks up whatever is under the pointer.
     *
     * @return true if something was picked up, so a caller knows the press was consumed
     */
    public boolean press(ViewModel view, int scrollOffset, double y) {
        heldKey = null;
        moved = false;
        pressedY = y;
        pointerY = y;
        Optional<ViewLayout.Region> region = ViewLayout.rowAt(view, scrollOffset, y);
        if (region.isEmpty()) {
            return false;
        }
        ViewModel.Row row = rowOf(view, region.get());
        if (!row.draggable()) {
            // A statement rather than a control - "no add-ons installed", a diagnostic with no pack.
            // Picking one up would give the user a row that can never be dropped anywhere.
            return false;
        }
        heldKey = row.key();
        return true;
    }

    /** Follows the pointer. */
    public void moveTo(double y) {
        pointerY = y;
        if (Math.abs(y - pressedY) > SLOP) {
            moved = true;
        }
    }

    /** True once a press has travelled far enough to be a drag rather than a click. */
    public boolean dragging() {
        return heldKey != null && moved;
    }

    /** True while a row is held, whether or not it has moved yet. */
    public boolean holding() {
        return heldKey != null;
    }

    /** The held row's key, for a caller that wants to select it on a click. */
    public Optional<String> heldKey() {
        return Optional.ofNullable(heldKey);
    }

    /** The held row as it exists in the current view, which may have been rebuilt since. */
    public Optional<ViewModel.Row> heldRow(ViewModel view) {
        return heldKey == null ? Optional.empty() : find(view, heldKey);
    }

    /** Where the pointer is, for drawing the row in hand. */
    public double pointerY() {
        return pointerY;
    }

    /** Where the held row would land if released now, for drawing the insertion mark. */
    public Optional<ViewLayout.Drop> target(ViewModel view, int scrollOffset) {
        if (!dragging()) {
            return Optional.empty();
        }
        return ViewLayout.dropAt(view, scrollOffset, pointerY)
                .filter(drop -> view.sections().get(drop.section()).drop().isPresent());
    }

    /**
     * Drops the held row and reports what to run.
     *
     * <p>Empty for a click that never became a drag, for a drop outside any section that accepts
     * one, and for a drop that would leave the pack exactly where it already is. The last is the
     * section's decision, not this class's: only the section knows whether the position it was given
     * differs from the one the pack has.
     *
     * <p>Always clears the held row, including on every one of those paths. A drag that silently
     * stayed live after an unsuccessful drop would move the next row the user merely clicked on.
     */
    public List<String> release(ViewModel view, int scrollOffset) {
        Optional<ViewModel.Row> row = heldRow(view);
        Optional<ViewLayout.Drop> drop = target(view, scrollOffset);
        heldKey = null;
        moved = false;
        if (row.isEmpty() || drop.isEmpty()) {
            return List.of();
        }
        return view.sections().get(drop.get().section()).drop().orElseThrow()
                .commands(row.get(), drop.get().position());
    }

    /** Lets go without doing anything — for a screen closing mid-drag. */
    public void cancel() {
        heldKey = null;
        moved = false;
    }

    private static ViewModel.Row rowOf(ViewModel view, ViewLayout.Region region) {
        return view.sections().get(region.section()).rows().get(region.row());
    }

    private static Optional<ViewModel.Row> find(ViewModel view, String key) {
        return view.sections().stream()
                .flatMap(section -> section.rows().stream())
                .filter(row -> row.key().equals(key))
                .findFirst();
    }
}
