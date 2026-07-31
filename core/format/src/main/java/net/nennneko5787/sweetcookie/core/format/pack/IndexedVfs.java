package net.nennneko5787.sweetcookie.core.format.pack;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * A {@link PackVfs} over an index built once, reading bytes on demand. SC-100 §9.
 *
 * <p>The index is a {@code LinkedHashMap} keyed by lowercase path, holding the path as the archive
 * spelled it. Insertion order is archive order, which is what makes {@link #paths()} deterministic —
 * and SC-110 §10 requires determinism all the way down, because the block ledger depends on it.
 *
 * <p>Nothing is decompressed at index time. A 300 MB add-on costs one pass over its central
 * directory, and the fraction of it that is ever read is usually small.
 */
@SpecImpl("SC-100")
public final class IndexedVfs implements PackVfs {

    /** One indexed entry. {@code path} keeps the archive's own spelling, for diagnostics. */
    public record Entry(String path, long size, ByteSource bytes) {
    }

    private final Map<String, Entry> byKey;
    private final Closeable owner;

    IndexedVfs(Map<String, Entry> byKey, Closeable owner) {
        this.byKey = byKey;
        this.owner = owner;
    }

    /** An in-memory VFS, for tests and for content synthesised rather than read. */
    public static IndexedVfs of(Map<String, byte[]> files) {
        Map<String, Entry> index = new LinkedHashMap<>();
        files.forEach((path, bytes) -> {
            String normalised = VfsPath.normalise(path);
            index.put(VfsPath.key(normalised),
                    new Entry(normalised, bytes.length, ByteSource.of(bytes)));
        });
        return new IndexedVfs(index, null);
    }

    @Override
    public Optional<ByteSource> read(String path) {
        return Optional.ofNullable(byKey.get(VfsPath.normalisedKey(path))).map(Entry::bytes);
    }

    @Override
    public boolean exists(String path) {
        return byKey.containsKey(VfsPath.normalisedKey(path));
    }

    /** The indexed entry, with its size and the spelling the archive used. */
    public Optional<Entry> entry(String path) {
        return Optional.ofNullable(byKey.get(VfsPath.normalisedKey(path)));
    }

    @Override
    public List<String> list(String directory) {
        String prefix = VfsPath.normalisedKey(directory);
        // A directory has no entry of its own in a zip we can rely on, so children are derived from
        // the paths of the files under it. LinkedHashSet keeps archive order and collapses the
        // repeats that a subdirectory with many files would otherwise produce.
        LinkedHashSet<String> children = new LinkedHashSet<>();
        for (Entry entry : byKey.values()) {
            String key = VfsPath.key(entry.path());
            if (!VfsPath.isUnder(key, prefix)) {
                continue;
            }
            String relative = prefix.isEmpty()
                    ? entry.path()
                    : entry.path().substring(prefix.length() + 1);
            int slash = relative.indexOf('/');
            String child = slash < 0 ? relative : relative.substring(0, slash);
            children.add(prefix.isEmpty() ? child : entry.path().substring(0, prefix.length() + 1)
                    + child);
        }
        return List.copyOf(children);
    }

    @Override
    public Stream<String> walk(String directory) {
        String prefix = VfsPath.normalisedKey(directory);
        return byKey.values().stream()
                .map(Entry::path)
                .filter(path -> VfsPath.isUnder(VfsPath.key(path), prefix));
    }

    @Override
    public Stream<String> paths() {
        return byKey.values().stream().map(Entry::path);
    }

    /** Every entry, in archive order. */
    public Collection<Entry> entries() {
        return byKey.values();
    }

    @Override
    public PackVfs rooted(String prefix) {
        String key = VfsPath.normalisedKey(prefix);
        if (key.isEmpty()) {
            return this;
        }
        Map<String, Entry> view = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> e : byKey.entrySet()) {
            if (!VfsPath.isUnder(e.getKey(), key)) {
                continue;
            }
            Entry entry = e.getValue();
            String relative = entry.path().substring(key.length() + 1);
            view.put(e.getKey().substring(key.length() + 1),
                    new Entry(relative, entry.size(), entry.bytes()));
        }
        // No owner: closing a view must not close the archive its siblings are still reading.
        return new IndexedVfs(view, null);
    }

    @Override
    public void close() {
        if (owner == null) {
            return;
        }
        try {
            owner.close();
        } catch (IOException e) {
            // Nothing useful can be done, and core/ has no logger (SC-000 §10). A failure to close
            // costs a file handle until the process exits; throwing here would abort a reload that
            // has otherwise succeeded, which is strictly worse.
        }
    }

    /** Builder used by {@link PackArchives}; enforces the index-time half of SC-100 §3. */
    static final class Builder {
        private final Map<String, Entry> index = new LinkedHashMap<>();
        private final List<String> caseCollisions = new ArrayList<>();

        boolean add(String normalisedPath, long size, ByteSource bytes) {
            String key = VfsPath.key(normalisedPath);
            if (index.containsKey(key)) {
                // SC-100 §3: the first in archive order wins. Reporting is the caller's, because
                // only it knows the provenance.
                caseCollisions.add(normalisedPath);
                return false;
            }
            index.put(key, new Entry(normalisedPath, size, bytes));
            return true;
        }

        int size() {
            return index.size();
        }

        List<String> caseCollisions() {
            return caseCollisions;
        }

        IndexedVfs build(Closeable owner) {
            return new IndexedVfs(index, owner);
        }
    }
}
