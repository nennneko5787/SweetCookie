package net.nennneko5787.sweetcookie.core.format.pack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * A stack of {@link PackVfs} layers, highest precedence first. SC-100 §7, §9.
 *
 * <p>This is how a subpack works: files under {@code subpacks/<folder>/} are remapped to
 * root-relative and laid over the pack root. An overlay rather than a copy, so that reloading with a
 * different memory tier selected costs nothing — no re-extraction, no second index.
 *
 * <p>Only within one pack. There is no cross-pack layer here on purpose (SC-100 §9): Bedrock's merge
 * rules differ per content type, and a flat lookup would erase the difference between "a later
 * resource pack replaced this texture", which is normal, and "two behavior packs define this
 * entity", which is a bug worth telling the author about.
 */
@SpecImpl("SC-100")
public final class LayeredVfs implements PackVfs {

    private final List<PackVfs> layers;

    /** @param layers highest precedence first */
    public LayeredVfs(List<PackVfs> layers) {
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("a layered VFS needs at least one layer");
        }
        this.layers = List.copyOf(layers);
    }

    /** {@code base} with {@code overlay} laid over it. */
    public static PackVfs over(PackVfs overlay, PackVfs base) {
        return new LayeredVfs(List.of(overlay, base));
    }

    @Override
    public Optional<ByteSource> read(String path) {
        for (PackVfs layer : layers) {
            Optional<ByteSource> found = layer.read(path);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean exists(String path) {
        return layers.stream().anyMatch(layer -> layer.exists(path));
    }

    @Override
    public List<String> list(String directory) {
        return distinctByKey(layers.stream().flatMap(layer -> layer.list(directory).stream()));
    }

    @Override
    public Stream<String> walk(String directory) {
        return distinctByKey(layers.stream().flatMap(layer -> layer.walk(directory))).stream();
    }

    @Override
    public Stream<String> paths() {
        return distinctByKey(layers.stream().flatMap(PackVfs::paths)).stream();
    }

    @Override
    public PackVfs rooted(String prefix) {
        return new LayeredVfs(layers.stream().map(layer -> layer.rooted(prefix)).toList());
    }

    @Override
    public void close() {
        layers.forEach(PackVfs::close);
    }

    /**
     * De-duplicates case-insensitively while keeping the highest layer's spelling.
     *
     * <p>A subpack that ships {@code Textures/Blocks/Foo.PNG} over a root's
     * {@code textures/blocks/foo.png} is one file, not two, and listing it twice would make an
     * asset-pipeline walk read and upload it twice.
     */
    private static List<String> distinctByKey(Stream<String> paths) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        paths.forEach(path -> {
            if (keys.add(VfsPath.key(path))) {
                out.add(path);
            }
        });
        return List.copyOf(out);
    }
}
