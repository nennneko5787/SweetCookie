package net.nennneko5787.sweetcookie.core.ui;

import java.util.ArrayList;
import java.util.List;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.Severity;

/**
 * Turns a {@link ViewModel} into positioned, coloured lines. SC-280 §3, §5.
 *
 * <p>Version-free and Minecraft-free, so it is testable headlessly the way SC-280 §7 asks for:
 * build every screen with a synthetic pack set and assert the line list rather than a screenshot.
 * A backend supplies {@link ViewRenderer} and nothing else.
 */
@SpecImpl("SC-280")
public final class ViewLayout {

    /** One laid-out line. */
    public record Line(String text, int x, int y, int argb) {
    }

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
        List<Line> lines = new ArrayList<>();
        int y = MARGIN_Y - scrollOffset;
        int actionable = 0;

        lines.add(new Line(view.title(), MARGIN_X, y, TITLE));
        y += LINE_HEIGHT * 2;

        for (ViewModel.Section section : view.sections()) {
            if (section.rows().isEmpty()) {
                continue;
            }
            lines.add(new Line(section.heading(), MARGIN_X, y, HEADING));
            y += LINE_HEIGHT;

            for (ViewModel.Row row : section.rows()) {
                boolean isSelected = !row.actions().isEmpty() && actionable++ == selected;
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
            }
            y += LINE_HEIGHT;
        }
        return lines;
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
