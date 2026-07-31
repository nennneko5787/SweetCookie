package net.nennneko5787.sweetcookie.core.format.pack;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * A view of another {@link PackVfs} with one directory hidden. SC-100 §7.
 *
 * <p>Exists for {@code subpacks/}. Once a variant has been selected and layered over the pack root,
 * the {@code subpacks/} tree is no longer content — it is the container the variants came from, and
 * every variant that was <em>not</em> selected is still sitting in it. Leaving it visible means an
 * asset walk finds {@code subpacks/sd/textures/a.png} as an ordinary texture and registers a file
 * the player was never meant to receive, in addition to the one they were.
 *
 * <p>Found by reading a conformance golden rather than by reasoning, which is the argument for
 * goldens listing the resolved file set at all.
 */
@SpecImpl("SC-100")
final class ExcludingVfs implements PackVfs {

    private final PackVfs delegate;
    private final String hiddenKey;

    ExcludingVfs(PackVfs delegate, String hidden) {
        this.delegate = delegate;
        this.hiddenKey = VfsPath.normalisedKey(hidden);
    }

    private boolean hidden(String path) {
        String key = VfsPath.key(path);
        return key.equals(hiddenKey) || VfsPath.isUnder(key, hiddenKey);
    }

    @Override
    public Optional<ByteSource> read(String path) {
        return hidden(VfsPath.normalise(path)) ? Optional.empty() : delegate.read(path);
    }

    @Override
    public boolean exists(String path) {
        return !hidden(VfsPath.normalise(path)) && delegate.exists(path);
    }

    @Override
    public List<String> list(String directory) {
        return delegate.list(directory).stream().filter(path -> !hidden(path)).toList();
    }

    @Override
    public Stream<String> walk(String directory) {
        return delegate.walk(directory).filter(path -> !hidden(path));
    }

    @Override
    public Stream<String> paths() {
        return delegate.paths().filter(path -> !hidden(path));
    }

    @Override
    public PackVfs rooted(String prefix) {
        // Rebasing into the hidden directory would un-hide it, which is the one thing this view
        // exists to prevent.
        if (hidden(VfsPath.normalise(prefix))) {
            return IndexedVfs.of(java.util.Map.of());
        }
        return delegate.rooted(prefix);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
