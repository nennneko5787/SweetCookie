package net.nennneko5787.sweetcookie.runtime.ui;

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
     * One line.
     *
     * @param label  the primary text: a pack name, a size class, a setting
     * @param detail the secondary text: a version, a count, what a pack provides
     * @param badge  the worst severity reported against this row, when anything was
     * @param notes  lines shown under the row — the diagnostics themselves, quoted
     */
    public record Row(String label, String detail, Optional<Severity> badge, List<String> notes) {

        public Row {
            notes = List.copyOf(notes);
        }

        public static Row of(String label, String detail) {
            return new Row(label, detail, Optional.empty(), List.of());
        }

        public static Row of(String label, String detail, Severity badge, List<String> notes) {
            return new Row(label, detail, Optional.of(badge), notes);
        }

        /** A row with nothing to say, so a section can state that rather than appear broken. */
        public static Row empty(String label) {
            return new Row(label, "", Optional.empty(), List.of());
        }
    }

    /** True when nothing at all would be drawn, so a caller can say so instead of drawing nothing. */
    public boolean isEmpty() {
        return sections.stream().allMatch(section -> section.rows().isEmpty());
    }
}
