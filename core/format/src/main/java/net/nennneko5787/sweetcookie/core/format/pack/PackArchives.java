package net.nennneko5787.sweetcookie.core.format.pack;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostics;
import net.nennneko5787.sweetcookie.core.format.diag.FormatDiagnostics;
import net.nennneko5787.sweetcookie.core.format.value.PackId;
import net.nennneko5787.sweetcookie.core.format.value.Provenance;

/**
 * Opens a container as an {@link IndexedVfs}, enforcing SC-100 §3. Nothing is extracted to disk.
 *
 * <p>Every limit aborts the offending <b>pack</b> and returns empty, never the whole load: an add-on
 * with one hostile pack in it should still give the user the other four.
 *
 * <h2>What is not checked, and why</h2>
 *
 * <p>SC-100 §3 allocates {@code SCE-1002} for symlink and other non-regular entries. It is
 * <b>never emitted</b>, for two reasons that are worth stating rather than leaving as a silent gap.
 * {@code java.util.zip} does not expose a zip entry's external file attributes, so the Unix mode
 * bits that mark a symlink are not reachable without parsing the central directory by hand. More
 * importantly the check would have nothing to protect: entries are read into memory on demand and
 * are never materialised on a filesystem, so a symlink entry is only ever a file whose contents
 * happen to be a path. <b>A future stage that does write entries to disk must reinstate this
 * check</b> — that is the point at which it stops being theoretical.
 */
@SpecImpl("SC-100")
public final class PackArchives {

    private PackArchives() {
    }

    /**
     * Indexes a directory on disk.
     *
     * <p>Entries are sorted by path so that two machines with different filesystem enumeration
     * orders produce the same load order (SC-000 §9). A zip has a declared order and is left in it;
     * a directory does not, so one is imposed.
     */
    public static Optional<PackVfs> openDirectory(
            Path root, PackSource source, ExtractionLimits limits, Diagnostics into) {
        Provenance where = Provenance.file(PackId.NONE, source.toString());
        List<Path> files;
        try (var walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> root.relativize(p).toString()))
                    .toList();
        } catch (IOException e) {
            into.report(FormatDiagnostics.MANIFEST_UNUSABLE.at(where, e.toString()));
            return Optional.empty();
        }

        IndexedVfs.Builder builder = new IndexedVfs.Builder();
        long total = 0;
        for (Path file : files) {
            String raw = root.relativize(file).toString();
            VfsPath.Inspection inspection = VfsPath.inspect(raw, limits.maxPathLength());
            if (!inspection.accepted()) {
                report(inspection.rejection().orElseThrow(), raw, where, into);
                return Optional.empty();
            }
            long size;
            try {
                size = Files.size(file);
            } catch (IOException e) {
                into.report(FormatDiagnostics.MANIFEST_UNUSABLE.at(where, raw, e.toString()));
                return Optional.empty();
            }
            if (size > limits.maxFileBytes()) {
                into.report(FormatDiagnostics.ENTRY_TOO_LARGE.at(where, raw, size));
                return Optional.empty();
            }
            total += size;
            if (total > limits.totalUncompressedBytes()) {
                into.report(FormatDiagnostics.ARCHIVE_TOO_LARGE.at(where, total));
                return Optional.empty();
            }
            if (builder.size() >= limits.maxEntries()) {
                into.report(FormatDiagnostics.ARCHIVE_TOO_MANY_ENTRIES.at(where, limits.maxEntries()));
                return Optional.empty();
            }
            builder.add(inspection.path(), size, () -> Files.readAllBytes(file));
        }
        reportCollisions(builder, where, into);
        return Optional.of(builder.build(null));
    }

    /**
     * Indexes a ZIP on disk, keeping it open so that entries can be read lazily.
     *
     * <p>The returned VFS owns the {@link ZipFile} and closes it. SC-100 §12 requires that: on
     * Windows, which is what most add-on authors use, an open handle stops the user replacing the
     * file they are editing, so a reload must release it before re-reading.
     */
    public static Optional<PackVfs> openZip(
            Path file, PackSource source, ExtractionLimits limits, Diagnostics into) {
        Provenance where = Provenance.file(PackId.NONE, source.toString());
        ZipFile zip;
        try {
            zip = openWithEntryNameFallback(file);
        } catch (IOException e) {
            into.report(FormatDiagnostics.MANIFEST_UNUSABLE.at(where, e.toString()));
            return Optional.empty();
        }

        IndexedVfs.Builder builder = new IndexedVfs.Builder();
        long total = 0;
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            if (builder.size() >= limits.maxEntries()) {
                into.report(FormatDiagnostics.ARCHIVE_TOO_MANY_ENTRIES.at(where, limits.maxEntries()));
                return closeAndFail(zip);
            }
            VfsPath.Inspection inspection = VfsPath.inspect(entry.getName(), limits.maxPathLength());
            if (!inspection.accepted()) {
                report(inspection.rejection().orElseThrow(), entry.getName(), where, into);
                return closeAndFail(zip);
            }
            long size = entry.getSize();
            if (size > limits.maxFileBytes()) {
                into.report(FormatDiagnostics.ENTRY_TOO_LARGE.at(where, entry.getName(), size));
                return closeAndFail(zip);
            }
            if (limits.isRatioSuspicious(size, entry.getCompressedSize())) {
                into.report(FormatDiagnostics.ARCHIVE_TOO_LARGE.at(
                        where, entry.getName(), size, entry.getCompressedSize()));
                return closeAndFail(zip);
            }
            if (size > 0) {
                total += size;
                if (total > limits.totalUncompressedBytes()) {
                    into.report(FormatDiagnostics.ARCHIVE_TOO_LARGE.at(where, total));
                    return closeAndFail(zip);
                }
            }
            long cap = limits.maxFileBytes();
            String name = entry.getName();
            builder.add(inspection.path(), Math.max(size, 0), () -> {
                try (InputStream in = zip.getInputStream(zip.getEntry(name))) {
                    return readAtMost(in, cap, name);
                }
            });
        }
        reportCollisions(builder, where, into);
        return Optional.of(builder.build(zip));
    }

    /**
     * Opens a ZIP whose entry names may not be UTF-8.
     *
     * <p><b>A real add-on failed on this.</b> A {@code .mcpack} with Japanese file names inside was
     * rejected outright with {@code invalid CEN header (bad entry name or comment)} — a message that
     * says "corrupt" and means nothing of the kind. The archive was fine; ZIP only carries a flag
     * saying names are UTF-8, and an archiver on a Japanese Windows writes them in the system code
     * page and leaves the flag clear. The JDK assumes UTF-8 and refuses the whole file.
     *
     * <p>So: UTF-8 first, because that is what a correct modern archive uses and what the flag,
     * when set, means. Then the platform's own charset, which is what created the file on the
     * machine most likely to be reading it. Then ISO-8859-1, which maps every byte to a character
     * and therefore <b>cannot</b> fail — the names come out as mojibake and the pack loads, which is
     * the right end of the trade against refusing a working add-on.
     *
     * <p>No diagnostic. The names are the author's, not the user's, and there is nothing the person
     * seeing the message could do about the code page a stranger's archiver used.
     */
    private static ZipFile openWithEntryNameFallback(Path file) throws IOException {
        IOException first = null;
        for (Charset charset : ENTRY_NAME_CHARSETS) {
            try {
                return new ZipFile(file.toFile(), charset);
            } catch (ZipException | IllegalArgumentException rejected) {
                // IllegalArgumentException as well as ZipException: the JDK reports an undecodable
                // entry name as "malformed input", which is not an IOException at all.
                if (first == null) {
                    first = rejected instanceof IOException io
                            ? io
                            : new ZipException(rejected.toString());
                }
            }
        }
        throw first;
    }

    /**
     * The same ladder for an archive already in memory — a pack nested in an {@code .mcaddon}.
     *
     * <p>Chosen by reading the names through once rather than by retrying the real pass, because the
     * real pass reports diagnostics and running it twice would report them twice. ISO-8859-1 cannot
     * fail, so the loop always terminates with an answer.
     */
    private static Charset entryNameCharset(byte[] archive) {
        for (Charset charset : ENTRY_NAME_CHARSETS) {
            try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(archive), charset)) {
                while (in.getNextEntry() != null) {
                    // Names only. Nothing is decompressed by getNextEntry.
                }
                return charset;
            } catch (IOException | IllegalArgumentException rejected) {
                // Next charset.
            }
        }
        return StandardCharsets.ISO_8859_1;
    }

    /** UTF-8, then what this machine writes, then one that maps every byte. See above. */
    private static final List<Charset> ENTRY_NAME_CHARSETS = List.of(
            StandardCharsets.UTF_8, Charset.defaultCharset(), StandardCharsets.ISO_8859_1);

    /**
     * Indexes a ZIP held in memory, which is how a pack nested inside an {@code .mcaddon} is read.
     *
     * <p>Contents are decompressed eagerly here, unlike the other two. A nested archive has already
     * been read into memory to get at it, so laziness would save nothing, and {@link ZipInputStream}
     * cannot seek back to an entry afterwards anyway.
     */
    public static Optional<PackVfs> openZipBytes(
            byte[] archive, PackSource source, ExtractionLimits limits, Diagnostics into) {
        Provenance where = Provenance.file(PackId.NONE, source.toString());
        IndexedVfs.Builder builder = new IndexedVfs.Builder();
        long total = 0;

        try (ZipInputStream in = new ZipInputStream(
                new ByteArrayInputStream(archive), entryNameCharset(archive))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (builder.size() >= limits.maxEntries()) {
                    into.report(FormatDiagnostics.ARCHIVE_TOO_MANY_ENTRIES.at(
                            where, limits.maxEntries()));
                    return Optional.empty();
                }
                VfsPath.Inspection inspection =
                        VfsPath.inspect(entry.getName(), limits.maxPathLength());
                if (!inspection.accepted()) {
                    report(inspection.rejection().orElseThrow(), entry.getName(), where, into);
                    return Optional.empty();
                }
                // A streamed entry's declared size is often -1, so the only trustworthy bound is the
                // count of bytes that actually arrive. readAtMost throws rather than truncating: a
                // truncated file would parse as merely broken and hide the real reason.
                byte[] bytes;
                try {
                    bytes = readAtMost(in, limits.maxFileBytes(), entry.getName());
                } catch (IOException oversized) {
                    into.report(FormatDiagnostics.ENTRY_TOO_LARGE.at(
                            where, entry.getName(), limits.maxFileBytes()));
                    return Optional.empty();
                }
                total += bytes.length;
                if (total > limits.totalUncompressedBytes()) {
                    into.report(FormatDiagnostics.ARCHIVE_TOO_LARGE.at(where, total));
                    return Optional.empty();
                }
                builder.add(inspection.path(), bytes.length, ByteSource.of(bytes));
            }
        } catch (ZipException e) {
            into.report(FormatDiagnostics.MANIFEST_UNUSABLE.at(where, e.toString()));
            return Optional.empty();
        } catch (IOException e) {
            into.report(FormatDiagnostics.MANIFEST_UNUSABLE.at(where, e.toString()));
            return Optional.empty();
        }
        reportCollisions(builder, where, into);
        return Optional.of(builder.build(null));
    }

    /** Reads at most {@code cap} bytes, and fails rather than truncating past it. */
    private static byte[] readAtMost(InputStream in, long cap, String name) throws IOException {
        byte[] bytes = in.readNBytes((int) Math.min(cap + 1, Integer.MAX_VALUE));
        if (bytes.length > cap) {
            throw new IOException("entry exceeds the per-file limit: " + name);
        }
        return bytes;
    }

    private static Optional<PackVfs> closeAndFail(ZipFile zip) {
        try {
            zip.close();
        } catch (IOException ignored) {
            // The pack is already being abandoned; a failure to close cannot make that worse.
        }
        return Optional.empty();
    }

    private static void report(
            VfsPath.Rejection why, String raw, Provenance where, Diagnostics into) {
        switch (why) {
            case ESCAPES_ROOT -> into.report(FormatDiagnostics.ENTRY_PATH_ESCAPES.at(where, raw));
            case TOO_LONG -> into.report(FormatDiagnostics.ENTRY_PATH_TOO_LONG.at(where, raw));
            case NOT_NORMALISED ->
                    into.report(FormatDiagnostics.ENTRY_NAME_NOT_NORMALISED.at(where, raw));
        }
    }

    private static void reportCollisions(
            IndexedVfs.Builder builder, Provenance where, Diagnostics into) {
        List<String> collisions = new ArrayList<>(builder.caseCollisions());
        for (String path : collisions) {
            // A collision is a warning, not a refusal: the pack still works, with the first entry
            // winning, and refusing would reject packs that Bedrock itself loads on Windows.
            into.report(FormatDiagnostics.ENTRY_CASE_COLLISION.at(where, path),
                    List.of(where, path));
        }
    }
}
