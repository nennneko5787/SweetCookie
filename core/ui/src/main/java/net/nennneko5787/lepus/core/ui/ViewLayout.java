package net.nennneko5787.lepus.core.ui;

import java.util.ArrayList;
import java.util.List;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.diag.Severity;

/**
 * Turns a {@link ViewModel} into positioned, coloured lines. SC-280 §3, §5.
 *
 * <p>Version-free and Minecraft-free, so it is testable headlessly the way SC-280 §7 asks for:
 * build every screen with a synthetic pack set and assert the line list rather than a screenshot.
 * A backend supplies {@link ViewRenderer} and nothing else.
 *
 * <p><b>Read-only.</b> Pack selection is Minecraft's own {@code PackSelectionScreen} (SC-280 §5.2),
 * so nothing laid out here is a control: this draws the pool view, the ledger, and the list a client
 * sees when it is not the process running the world.
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

    private ViewLayout() {
    }

    /**
     * Lays a view out from the top-left.
     *
     * <p>No wrapping: it needs a font's measurements, which is a second thing for a backend to
     * provide, and it is not needed for what this draws. A row wider than the screen is a real
     * limitation and is stated rather than hidden.
     */
    public static List<Line> lay(ViewModel view, int scrollOffset) {
        List<Line> lines = new ArrayList<>();
        int y = MARGIN_Y - scrollOffset;

        lines.add(new Line(view.title(), MARGIN_X, y, TITLE));
        y += LINE_HEIGHT * 2;

        for (ViewModel.Section section : view.sections()) {
            if (section.rows().isEmpty()) {
                continue;
            }
            lines.add(new Line(section.heading(), MARGIN_X, y, HEADING));
            y += LINE_HEIGHT;

            for (ViewModel.Row row : section.rows()) {
                int colour = row.badge().map(ViewLayout::colourOf).orElse(BODY);
                lines.add(new Line(row.label(), MARGIN_X + 8, y, colour));
                if (!row.detail().isEmpty()) {
                    lines.add(new Line(row.detail(), MARGIN_X + 20, y + LINE_HEIGHT, DETAIL));
                    y += LINE_HEIGHT;
                }
                y += LINE_HEIGHT;
                for (String note : row.notes()) {
                    lines.add(new Line(note, MARGIN_X + 20, y, ERROR));
                    y += LINE_HEIGHT;
                }
            }
            y += LINE_HEIGHT;
        }
        return lines;
    }

    /** Draws a laid-out view through a backend. */
    public static void draw(ViewModel view, ViewRenderer renderer, int scrollOffset) {
        for (Line line : lay(view, scrollOffset)) {
            renderer.line(line.text(), line.x(), line.y(), line.argb());
        }
    }

    /** The total height a view occupies, so a backend can bound its own scrolling. */
    public static int height(ViewModel view) {
        List<Line> lines = lay(view, 0);
        return lines.isEmpty() ? 0 : lines.get(lines.size() - 1).y() + LINE_HEIGHT;
    }

    private static int colourOf(Severity severity) {
        return switch (severity) {
            case ERROR -> ERROR;
            case WARNING -> WARNING;
            case INFO -> INFO;
        };
    }
}
