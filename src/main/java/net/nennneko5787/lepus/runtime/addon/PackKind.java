package net.nennneko5787.lepus.runtime.addon;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import net.nennneko5787.lepus.core.api.SpecImpl;

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

    BEHAVIOR("Behavior packs", "be_behavior_pack", PackSummary::behavior),
    RESOURCE("Resource packs", "be_resource_pack", PackSummary::resource);

    private final String title;
    private final String folder;
    private final Predicate<PackSummary> includes;

    PackKind(String title, String folder, Predicate<PackSummary> includes) {
        this.title = title;
        this.folder = folder;
        this.includes = includes;
    }

    /** The tab label. */
    public String title() {
        return title;
    }

    /**
     * Where packs of this kind are installed, under {@code PlatformInfo.addonRoot()}.
     *
     * <p>Two folders rather than one, because Bedrock has always split {@code behavior_packs} from
     * {@code resource_packs} and a folder of mixed {@code .mcaddon} files is not sortable by eye.
     * The {@code be_} prefix says these are Bedrock packs, so nobody drops a Java resource pack in
     * expecting it to work.
     *
     * <p><b>The folder organises; the manifest decides.</b> A pack filed under the wrong one still
     * loads as whatever its manifest says it is — the alternative is a file that silently does
     * nothing because it is in the wrong place, which is the failure this split was meant to avoid.
     */
    public Path directoryIn(Path addonRoot) {
        return addonRoot.resolve(folder);
    }

    /** Every kind's folder, in tab order. */
    public static List<Path> directoriesIn(Path addonRoot) {
        return List.of(BEHAVIOR.directoryIn(addonRoot), RESOURCE.directoryIn(addonRoot));
    }

    public boolean includes(PackSummary pack) {
        return includes.test(pack);
    }

    public PackKind other() {
        return this == BEHAVIOR ? RESOURCE : BEHAVIOR;
    }
}
