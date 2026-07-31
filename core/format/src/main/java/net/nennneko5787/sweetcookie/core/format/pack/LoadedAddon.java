package net.nennneko5787.sweetcookie.core.format.pack;

import java.io.Closeable;
import java.util.List;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.DiagnosticLog;
import net.nennneko5787.sweetcookie.core.format.value.PackId;

/**
 * What SC-100 produces: packs in resolved load order, and everything that went wrong getting there.
 * SC-100 §11.
 *
 * <p>{@link Closeable} because the packs hold open archives. SC-100 §12 requires them released
 * before a reload — on Windows an open {@code ZipFile} stops the author replacing the file they are
 * editing, which is the single most common thing an author does.
 *
 * @param packs       in resolved load order; index equals {@code loadOrder}, and higher wins
 * @param diagnostics deduplicated, with occurrence counts (SC-240 §3)
 * @param containers  the archives to close; not part of the model, but they must be released
 */
@SpecImpl("SC-100")
public record LoadedAddon(
        List<LoadedPack> packs, DiagnosticLog diagnostics, List<PackVfs> containers)
        implements Closeable {

    public LoadedAddon {
        packs = List.copyOf(packs);
        containers = List.copyOf(containers);
    }

    public static LoadedAddon empty(DiagnosticLog diagnostics) {
        return new LoadedAddon(List.of(), diagnostics, List.of());
    }

    public Optional<LoadedPack> byId(PackId id) {
        return packs.stream().filter(pack -> pack.id().equals(id)).findFirst();
    }

    /** Packs contributing behavior-pack content, in load order. */
    public List<LoadedPack> behaviorPacks() {
        return packs.stream().filter(pack -> pack.manifest().hasBehavior()).toList();
    }

    /** Packs contributing resource-pack content, in load order. */
    public List<LoadedPack> resourcePacks() {
        return packs.stream().filter(pack -> pack.manifest().hasResources()).toList();
    }

    @Override
    public void close() {
        containers.forEach(PackVfs::close);
    }
}
