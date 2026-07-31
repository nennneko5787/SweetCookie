package net.nennneko5787.sweetcookie.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.Severity;

/**
 * Turns a {@link ViewModel} into positioned, coloured lines. SC-280 §3, §5.
 *
 * <p>Version-free and Minecraft-free, so it is testable headlessly the way SC-280 §7 asks for:
 * build every screen with a synthetic pack set and assert the line list rather than a screenshot.
 * A backend supplies {@link ViewRenderer} and nothing else.
 *
 * <p>It also owns the geometry the mouse needs. Where a row sits and where a dropped row would land
 * are answered here rather than in the screen, for the same reason the lines are: they are the same
 * answers on both Minecraft versions, and up here they can be asserted without a client.
 */
@SpecImpl("SC-280")
public final class ViewLayout {

    /** One laid-out line. */
    public record Line(String text, int x, int y, int argb) {
    }

    /**
     * The vertical band a row occupies, for hit testing.
     *
     * @param section index into the view's sections
     * @param row     index into that section's rows, or {@link #SECTION_BODY} for the section itself
     */
    public record Region(int section, int row, int top, int bottom) {
        public boolean contains(double y) {
            return y >= top && y < bottom;
        }
    }

    /**
     * Where a dragged row would land if released now.
     *
     * @param section  the section it would land in
     * @param position the index among that section's rows, counting the dragged row if it is there
     * @param y        where to draw the insertion mark
     */
    public record Drop(int section, int position, int y) {
    }

    /** A {@link Region} covering a whole section rather than one of its rows. */
    public static final int SECTION_BODY = -1;

    private static final int LINE_HEIGHT = 12;
    private static final int MARGIN_X = 16;
    private static final int MARGIN_Y = 24;

    // Severity colours, chosen to stay legible on Minecraft's dark screen background rather than to
    // match a palette. A dedicated server's operator reads the text form; this is for the client.
    private static final int TITLE = 0xFFFFFFFF;
    private static final int HEADING = 0xFFA0A0A0;
    private static final int BODY = 0xFFDDDDDD;
    private static final int DETAIL = 0xFF999999;
    private static final int ERROR = 0xFFFF6B6B;
    private static final int WARNING = 0xFFFFC66B;
    private static final int INFO = 0xFF6BC6FF;
    private static final int SELECTED = 0xFFFFFFFF;
    private static final int HOTKEY = 0xFF7FD97F;
    private static final int HELD = 0xFF6B6B6B;
    private static final int INSERTION = 0xFF7FD9FF;

    /** Selects nothing, for a view with no actions and for callers that only want the height. */
    public static final int NOTHING_SELECTED = -1;

    private ViewLayout() {
    }

    /** Lays a view out with nothing selected. */
    public static List<Line> lay(ViewModel view, int scrollOffset) {
        return lay(view, scrollOffset, NOTHING_SELECTED);
    }

    /**
     * Lays a view out from the top-left.
     *
     * <p>No wrapping: it needs a font's measurements, which is a second thing for a backend to
     * provide, and it is not needed to make the development loop in SC-280 §1 usable. A row wider
     * than the screen is a real limitation and is stated rather than hidden.
     *
     * <p>The selected row is marked with a leading arrow and drawn brighter, and <b>its keys are
     * printed underneath it</b> rather than in a legend at the edge of the screen. A legend has to be
     * read and remembered; keys under the thing they act on are read once, where the eye already is,
     * and they change with the row — the top pack shows no "raise", because there is nothing above
     * it to raise past.
     *
     * @param selected an index into {@link ViewModel#actionable()}, or {@link #NOTHING_SELECTED}
     */
    public static List<Line> lay(ViewModel view, int scrollOffset, int selected) {
        return build(view, scrollOffset, selected, new ArrayList<>());
    }

    /**
     * The bands every row and section occupy, for hit testing.
     *
     * <p>Produced by the same pass that produces the lines, so a row cannot be drawn in one place
     * and grabbed in another — the failure that makes a hand-written hit test drift from its screen
     * the first time a row grows a diagnostic.
     */
    public static List<Region> regions(ViewModel view, int scrollOffset) {
        List<Region> regions = new ArrayList<>();
        build(view, scrollOffset, NOTHING_SELECTED, regions);
        return regions;
    }

    /** The row under a pointer, if any. */
    public static Optional<Region> rowAt(ViewModel view, int scrollOffset, double y) {
        return regions(view, scrollOffset).stream()
                .filter(region -> region.row() != SECTION_BODY && region.contains(y))
                .findFirst();
    }

    /**
     * Where a row released at this height would land.
     *
     * <p>The insertion point is decided by which <b>half</b> of a row the pointer is in, not which
     * row it is over: dropping onto the top half of a row means "before this one" and the bottom
     * half means "after it". That is what lets a pack be placed at either end of the list, which a
     * whole-row target cannot express — and it is what the insertion mark drawn at {@link Drop#y}
     * is showing.
     *
     * <p>A pointer in a section's empty space, below its last row, lands at the end. That is how a
     * pack is dropped into a section that has no rows yet, and refusing it would make the first
     * pack in an empty list undraggable.
     */
    public static Optional<Drop> dropAt(ViewModel view, int scrollOffset, double y) {
        List<Region> regions = regions(view, scrollOffset);
        for (Region region : regions) {
            if (region.row() == SECTION_BODY || !region.contains(y)) {
                continue;
            }
            boolean after = y >= (region.top() + region.bottom()) / 2.0;
            return Optional.of(new Drop(region.section(), region.row() + (after ? 1 : 0),
                    after ? region.bottom() : region.top()));
        }
        for (Region region : regions) {
            if (region.row() != SECTION_BODY || !region.contains(y)) {
                continue;
            }
            int rows = view.sections().get(region.section()).rows().size();
            int end = regions.stream()
                    .filter(r -> r.section() == region.section() && r.row() != SECTION_BODY)
                    .mapToInt(Region::bottom).max().orElse(region.top());
            return Optional.of(new Drop(region.section(), rows, end));
        }
        return Optional.empty();
    }

    /** Draws a laid-out view through a backend. */
    public static void draw(ViewModel view, ViewRenderer renderer, int scrollOffset) {
        draw(view, renderer, scrollOffset, NOTHING_SELECTED);
    }

    /** Draws a laid-out view through a backend, with one row selected. */
    public static void draw(ViewModel view, ViewRenderer renderer, int scrollOffset, int selected) {
        for (Line line : lay(view, scrollOffset, selected)) {
            renderer.line(line.text(), line.x(), line.y(), line.argb());
        }
    }

    /**
     * Draws a view with a drag in progress.
     *
     * <p>Two marks, because they answer two different questions. The <b>insertion mark</b> says
     * where the pack will land — Java Edition's screen answers this only by the pack physically
     * moving once you have already let go. The <b>held label</b> follows the pointer, so that a drag
     * across a long list does not lose track of which pack is in hand.
     */
    public static void draw(ViewModel view, ViewRenderer renderer, int scrollOffset, int selected,
            ViewDrag drag) {
        draw(view, renderer, scrollOffset, selected);
        if (!drag.dragging()) {
            return;
        }
        drag.target(view, scrollOffset).ifPresent(drop ->
                renderer.line("-".repeat(48), MARGIN_X + 8, drop.y() - LINE_HEIGHT / 2, INSERTION));
        drag.heldRow(view).ifPresent(row ->
                renderer.line("[ " + row.label() + " ]", MARGIN_X + 8,
                        (int) drag.pointerY() - LINE_HEIGHT / 2, HELD));
    }

    /** The total height a view occupies, so a backend can bound its own scrolling. */
    public static int height(ViewModel view) {
        List<Line> lines = lay(view, 0);
        return lines.isEmpty() ? 0 : lines.get(lines.size() - 1).y() + LINE_HEIGHT;
    }

    /**
     * A scroll offset that keeps the selected row on screen.
     *
     * <p>Without this the arrow keys walk the selection off the bottom edge and the screen appears
     * to stop responding. Scrolls by the least it can, so that a selection moving one row does not
     * throw the whole list to a new position and cost the user their place.
     */
    public static int scrollTo(ViewModel view, int selected, int screenHeight, int scrollOffset) {
        List<Line> lines = lay(view, 0, selected);
        int top = -1;
        int end = -1;
        boolean past = false;
        for (Line line : lines) {
            if (line.text().startsWith("> ")) {
                top = line.y();
                past = true;
            } else if (past) {
                // Everything the selection owns — its detail, its diagnostics, its key line — sits
                // between it and the next row, and all of it has to be on screen for the keys to be
                // readable. Measured rather than assumed, because a pack with four errors is taller
                // than one with none. The next row's label, or the next heading, ends the run: both
                // sit further left than anything a row owns.
                if (line.x() <= MARGIN_X + 8) {
                    break;
                }
                end = line.y() + LINE_HEIGHT;
            }
        }
        if (top < 0) {
            return scrollOffset;
        }
        if (end < top) {
            end = top + LINE_HEIGHT;
        }
        if (top - scrollOffset < MARGIN_Y) {
            return Math.max(0, top - MARGIN_Y);
        }
        if (end - scrollOffset > screenHeight) {
            return Math.max(0, end - screenHeight);
        }
        return scrollOffset;
    }

    /**
     * The one pass, producing lines and optionally regions.
     *
     * <p>One pass rather than two so that a row is grabbed exactly where it is drawn. Two
     * implementations of the same geometry stay in step until the first time one of them grows a
     * case the other does not.
     */
    private static List<Line> build(ViewModel view, int scrollOffset, int selected,
            List<Region> regions) {
        List<Line> lines = new ArrayList<>();
        int y = MARGIN_Y - scrollOffset;
        int actionable = 0;

        lines.add(new Line(view.title(), MARGIN_X, y, TITLE));
        y += LINE_HEIGHT * 2;

        for (int s = 0; s < view.sections().size(); s++) {
            ViewModel.Section section = view.sections().get(s);
            if (section.rows().isEmpty() && section.drop().isEmpty()) {
                continue;
            }
            int sectionTop = y;
            lines.add(new Line(section.heading(), MARGIN_X, y, HEADING));
            y += LINE_HEIGHT;

            for (int r = 0; r < section.rows().size(); r++) {
                ViewModel.Row row = section.rows().get(r);
                int rowTop = y;
                boolean isSelected = row.draggable() && actionable++ == selected;
                int colour = isSelected ? SELECTED : row.badge().map(ViewLayout::colourOf).orElse(BODY);
                lines.add(new Line((isSelected ? "> " : "  ") + row.label(), MARGIN_X + 8, y, colour));
                if (!row.detail().isEmpty()) {
                    lines.add(new Line(row.detail(), MARGIN_X + 20, y + LINE_HEIGHT, DETAIL));
                    y += LINE_HEIGHT;
                }
                y += LINE_HEIGHT;
                for (String note : row.notes()) {
                    lines.add(new Line(note, MARGIN_X + 20, y, ERROR));
                    y += LINE_HEIGHT;
                }
                if (isSelected) {
                    lines.add(new Line(keysOf(row), MARGIN_X + 20, y, HOTKEY));
                    y += LINE_HEIGHT;
                }
                regions.add(new Region(s, r, rowTop, y));
            }
            // The trailing gap belongs to the section, so that the empty space below the last row is
            // a place to drop into rather than dead screen.
            y += LINE_HEIGHT;
            regions.add(new Region(s, SECTION_BODY, sectionTop, y));
        }
        return lines;
    }

    /** The selected row's keys, as the keycap and what it does. */
    private static String keysOf(ViewModel.Row row) {
        StringBuilder keys = new StringBuilder();
        for (ViewModel.Action action : row.actions()) {
            if (keys.length() > 0) {
                keys.append("   ");
            }
            keys.append('[').append(action.hotkey()).append("] ").append(action.label());
        }
        return keys.toString();
    }

    private static int colourOf(Severity severity) {
        return switch (severity) {
            case ERROR -> ERROR;
            case WARNING -> WARNING;
            case INFO -> INFO;
        };
    }
}
