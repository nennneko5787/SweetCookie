package net.nennneko5787.sweetcookie.core.ui;

import java.util.List;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.Severity;

/**
 * What a SweetCookie screen shows, described rather than drawn. SC-280 §3, §3.1.
 *
 * <p>This layer exists because the two supported Minecraft versions do not share a rendering model
 * for screens: 1.21.11 draws through {@code Screen.render(GuiGraphics, …)} and 26.2 through
 * {@code Screen.extractRenderState(GuiGraphicsExtractor, …)}, with {@code GuiGraphics}'s text
 * methods gone entirely. A screen written against either cannot compile against the other, so
 * everything that survives both has to stop above the pixels.
 *
 * <p>It is useful before any pixels exist. Rendered as text it is what SC-280 §1's development loop
 * needs — enable, watch it fail, read why — and it is what {@code /sweetcookie} prints, so the
 * command and the screen cannot drift apart (§7).
 *
 * @param title    the screen or command heading
 * @param sections in display order
 */
@SpecImpl("SC-280")
public record ViewModel(String title, List<Section> sections) {

    public ViewModel {
        sections = List.copyOf(sections);
    }

    /** A group of rows under a heading. */
    public record Section(String heading, List<Row> rows) {
        public Section {
            rows = List.copyOf(rows);
        }

        public static Section of(String heading, List<Row> rows) {
            return new Section(heading, rows);
        }
    }

    /**
     * Something a row can do, named by the command that does it. SC-280 §7.
     *
     * <p>A command string rather than a callback, because §7 makes the command the operation and the
     * screen a caller of it. A screen runs this by sending it as a command, which costs no new
     * packet, is refused by the same permission check as typing it, and works unchanged through
     * ViaVersion because a chat command is vanilla traffic. It also gives the text backend something
     * exact to print: a user reading {@code /sweetcookie packs} on a headless server is told what to
     * type, not merely that reordering exists.
     *
     * @param label   what the action does, in words
     * @param hotkey  the key that triggers it on a screen, as the character printed on that key
     * @param command the command, without its leading slash
     */
    public record Action(String label, char hotkey, String command) {
    }

    /**
     * One line.
     *
     * @param label   the primary text: a pack name, a size class, a setting
     * @param detail  the secondary text: a version, a count, what a pack provides
     * @param badge   the worst severity reported against this row, when anything was
     * @param notes   lines shown under the row — the diagnostics themselves, quoted
     * @param actions what this row can do; empty makes the row a statement rather than a control
     */
    public record Row(String label, String detail, Optional<Severity> badge, List<String> notes,
            List<Action> actions) {

        public Row {
            notes = List.copyOf(notes);
            actions = List.copyOf(actions);
        }

        public static Row of(String label, String detail) {
            return new Row(label, detail, Optional.empty(), List.of(), List.of());
        }

        public static Row of(String label, String detail, Severity badge, List<String> notes) {
            return new Row(label, detail, Optional.of(badge), notes, List.of());
        }

        /** A row with nothing to say, so a section can state that rather than appear broken. */
        public static Row empty(String label) {
            return new Row(label, "", Optional.empty(), List.of(), List.of());
        }

        /** The same row, with things it can do. */
        public Row with(List<Action> actions) {
            return new Row(label, detail, badge, notes, actions);
        }
    }

    /** True when nothing at all would be drawn, so a caller can say so instead of drawing nothing. */
    public boolean isEmpty() {
        return sections.stream().allMatch(section -> section.rows().isEmpty());
    }

    /**
     * The rows that can do something, in display order.
     *
     * <p>What a keyboard cursor moves over. Rows without actions are skipped rather than selectable:
     * stepping through headings and diagnostics to reach the next pack is the thing that makes a list
     * tedious to operate.
     */
    public List<Row> actionable() {
        return sections.stream()
                .flatMap(section -> section.rows().stream())
                .filter(row -> !row.actions().isEmpty())
                .toList();
    }
}
