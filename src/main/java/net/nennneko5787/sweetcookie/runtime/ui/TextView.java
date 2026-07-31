package net.nennneko5787.sweetcookie.runtime.ui;

import java.util.ArrayList;
import java.util.List;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.Severity;

/**
 * Renders a {@link ViewModel} as lines. SC-280 §3.1.
 *
 * <p>The first backend, and the one that needs no Minecraft at all. It serves the log at startup and
 * every {@code /sweetcookie} command; a per-version screen backend renders the same descriptions
 * later, which is what stops the screen and the command from disagreeing (SC-280 §7).
 */
@SpecImpl("SC-280")
public final class TextView {

    private TextView() {
    }

    public static List<String> render(ViewModel view) {
        List<String> lines = new ArrayList<>();
        lines.add(view.title());
        for (ViewModel.Section section : view.sections()) {
            if (section.rows().isEmpty()) {
                continue;
            }
            lines.add("  " + section.heading());
            for (ViewModel.Row row : section.rows()) {
                StringBuilder line = new StringBuilder("    ");
                row.badge().ifPresent(severity -> line.append(marker(severity)).append(' '));
                line.append(row.label());
                if (!row.detail().isEmpty()) {
                    line.append("  ").append(row.detail());
                }
                lines.add(line.toString());
                // Notes are indented under their row rather than folded into the detail: a
                // diagnostic is what a user acts on, and a truncated one is worse than none.
                row.notes().forEach(note -> lines.add("        " + note));
            }
        }
        return lines;
    }

    /**
     * A one-character severity marker.
     *
     * <p>Text, not colour. A dedicated server's console has no colour to rely on, and this is the
     * surface an operator debugging a headless server actually reads.
     */
    private static String marker(Severity severity) {
        return switch (severity) {
            case ERROR -> "[!]";
            case WARNING -> "[*]";
            case INFO -> "[i]";
        };
    }
}
