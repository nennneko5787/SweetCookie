package net.nennneko5787.lepus.core.ui;

import java.util.List;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.diag.Severity;

/**
 * What a Lepus screen shows, described rather than drawn. SC-280 §3, §3.1.
 *
 * <p>This layer exists because the two supported Minecraft versions do not share a rendering model
 * for screens: 1.21.11 draws through {@code Screen.render(GuiGraphics, …)} and 26.2 through
 * {@code Screen.extractRenderState(GuiGraphicsExtractor, …)}, with {@code GuiGraphics}'s text
 * methods gone entirely. A screen written against either cannot compile against the other, so
 * everything that survives both has to stop above the pixels.
 *
 * <p>It is useful before any pixels exist. Rendered as text it is what SC-280 §1's development loop
 * needs — enable, watch it fail, read why — and it is what {@code /lepus} prints, so the
 * command and the screen cannot drift apart (§7).
 *
 * <p><b>Descriptions, not controls.</b> Pack selection is Minecraft's own screen (SC-280 §5.2);
 * this describes the pool, the ledger, and lists that are read rather than operated.
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
     * Something a row can do, named by the command that does it. SC-280 §7.1.
     *
     * <p>Not a callback: §7.1 makes the command the operation. This exists for the <b>text</b>
     * backend, which is what an operator on a headless server reads — a list that says a pack can be
     * reordered without saying how to do it has told them nothing. A client has a screen for this and
     * does not need the strings.
     *
     * @param label   what the action does, in words
     * @param command the command, without its leading slash
     */
    public record Action(String label, String command) {
    }

    /**
     * One line.
     *
     * @param label   the primary text: a pack name, a size class, a setting
     * @param detail  the secondary text: a version, a count, what a pack provides
     * @param badge   the worst severity reported against this row, when anything was
     * @param notes   lines shown under the row — the diagnostics themselves, quoted
     * @param actions the commands that act on this row, for the text backend
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

        /** The same row, with the commands that act on it. */
        public Row with(List<Action> actions) {
            return new Row(label, detail, badge, notes, actions);
        }
    }

    /** True when nothing at all would be drawn, so a caller can say so instead of drawing nothing. */
    public boolean isEmpty() {
        return sections.stream().allMatch(section -> section.rows().isEmpty());
    }
}
