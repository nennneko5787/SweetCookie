package net.nennneko5787.sweetcookie.core.format.pack;

import java.io.Closeable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * The one interface every stage after SC-100 reads a pack's files through. SC-100 §9.
 *
 * <p><b>Paths are case-insensitive, {@code /}-separated and root-relative.</b> Bedrock is authored
 * largely on case-insensitive filesystems and real packs reference {@code Textures/Blocks/Foo.PNG}
 * for {@code textures/blocks/foo.png}. A case-sensitive lookup here would make a large fraction of
 * published add-ons silently lose their textures on Linux servers only, which is the worst possible
 * shape for a bug.
 *
 * <p>A pack's VFS is a stack of layers, highest precedence first: the selected subpack, then the
 * pack root. There is deliberately <b>no cross-pack fallback</b> at this level — Bedrock's merge
 * semantics differ per content type (a texture is replaced wholesale; two behavior packs defining
 * one entity is a conflict with its own rule), and flattening both into one lookup would lose that
 * distinction. Cross-pack merge is SC-110 §9.1's job.
 *
 * <p>{@link Closeable} because the implementation may hold an open archive. SC-100 §12 requires the
 * handle to be released before a reload: on Windows — the platform most add-on authors use — an open
 * {@code ZipFile} stops the user replacing the file they are editing.
 */
@SpecImpl("SC-100")
public interface PackVfs extends Closeable {

    /** The entry at {@code path}, or empty. */
    Optional<ByteSource> read(String path);

    /** Immediate children of {@code directory}, non-recursive, as normalised paths. */
    List<String> list(String directory);

    /** Every entry under {@code directory}, recursively, as normalised paths. */
    Stream<String> walk(String directory);

    boolean exists(String path);

    /** Every entry, in the order the archive declared them. Used for deterministic iteration. */
    Stream<String> paths();

    /** A view rooted at {@code prefix}, for a pack that lives in a subdirectory of a container. */
    PackVfs rooted(String prefix);

    /**
     * Closing a view does not close what it views.
     *
     * <p>Only the VFS that owns the archive closes it. Otherwise closing one pack inside an
     * {@code .mcaddon} would pull the archive out from under its siblings.
     */
    @Override
    void close();
}
