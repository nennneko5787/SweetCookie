package net.nennneko5787.sweetcookie.runtime.addon;

import java.util.function.Predicate;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * The two tabs of the selection screen. SC-280 §5.2.
 *
 * <p>Bedrock separates behaviour packs from resource packs everywhere it lists them, and an
 * {@code .mcaddon} normally unpacks into one of each. A single list would show every add-on as two
 * adjacent rows with nothing to tell them apart, which is neither what Bedrock does nor what Java
 * Edition does.
 *
 * <p>A pack can be <b>both</b>: one manifest may declare a {@code data} module and a
 * {@code resources} module. Such a pack appears in both tabs, and enabling it in either enables the
 * whole pack — there is one pack and one activation entry, not two halves. That is the truth about
 * the file, so it is what the screen shows.
 */
@SpecImpl("SC-280")
public enum PackKind {

    BEHAVIOR("Behavior packs", PackSummary::behavior),
    RESOURCE("Resource packs", PackSummary::resource);

    private final String title;
    private final Predicate<PackSummary> includes;

    PackKind(String title, Predicate<PackSummary> includes) {
        this.title = title;
        this.includes = includes;
    }

    /** The tab label. */
    public String title() {
        return title;
    }

    public boolean includes(PackSummary pack) {
        return includes.test(pack);
    }

    public PackKind other() {
        return this == BEHAVIOR ? RESOURCE : BEHAVIOR;
    }
}
