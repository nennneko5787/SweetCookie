package net.nennneko5787.sweetcookie.runtime.addon;

import java.util.List;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostic;
import net.nennneko5787.sweetcookie.core.format.diag.Severity;
import net.nennneko5787.sweetcookie.core.format.ir.PackIr;
import net.nennneko5787.sweetcookie.core.format.value.PackId;

/**
 * One row of the add-on management screen. SC-280 §5.
 *
 * <p>Version-free and loader-free on purpose: SC-280 §3 builds the screens from descriptions like
 * this rather than from widgets, and that is not a stylistic preference. 26.2 replaced
 * {@code Screen.render(GuiGraphics, …)} with {@code extractRenderState(GuiGraphicsExtractor, …)} and
 * removed {@code GuiGraphics}'s text methods entirely — the two supported versions do not share a
 * rendering model for UI, so anything that survives both has to stop above it.
 *
 * <p>{@code provides} exists because SC-280 §5 asks for it directly: a user with a folder of
 * {@code .mcaddon} files cannot otherwise tell them apart, and neither can a developer with six
 * test packs.
 *
 * @param id          the pack's identity
 * @param name        raw; may be a {@code .lang} key, resolved by the caller against its locale
 * @param version     the pack version
 * @param source      where it came from, for the "open folder" action and for diagnostics
 * @param loadOrder   position in the resolved order; higher wins
 * @param provides    what it contributes, by content kind
 * @param diagnostics everything reported against this pack, deduplicated
 */
@SpecImpl("SC-280")
public record PackSummary(
        PackId id,
        String name,
        String version,
        String source,
        int loadOrder,
        Provides provides,
        List<Diagnostic> diagnostics) {

    /** Counts per content kind. Zero is meaningful: it distinguishes "none" from "not parsed yet". */
    public record Provides(int blocks, int geometries) {

        public boolean isEmpty() {
            return blocks == 0 && geometries == 0;
        }

        /** A one-line rendering: {@code 3 blocks, 12 geometries}. Empty when it provides nothing. */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            if (blocks > 0) {
                sb.append(blocks).append(blocks == 1 ? " block" : " blocks");
            }
            if (geometries > 0) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(geometries).append(geometries == 1 ? " geometry" : " geometries");
            }
            return sb.toString();
        }
    }

    public PackSummary {
        diagnostics = List.copyOf(diagnostics);
    }

    static PackSummary of(PackIr pack, List<Diagnostic> diagnostics) {
        return new PackSummary(
                pack.id(),
                pack.source().header().name(),
                pack.source().version().toString(),
                pack.source().source().toString(),
                pack.loadOrder(),
                new Provides(pack.behavior().blocks().size(), pack.resource().geometries().size()),
                diagnostics);
    }

    /**
     * The worst severity reported against this pack, or empty when nothing was.
     *
     * <p>The badge SC-280 §5 asks for. A pack with an error badge is one a user should look at
     * before wondering why their content is missing.
     */
    public java.util.Optional<Severity> badge() {
        return diagnostics.stream().map(Diagnostic::severity).max(java.util.Comparator.naturalOrder());
    }

    public long count(Severity severity) {
        return diagnostics.stream().filter(d -> d.severity() == severity).count();
    }
}
