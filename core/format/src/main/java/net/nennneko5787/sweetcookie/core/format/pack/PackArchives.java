package net.nennneko5787.sweetcookie.core.format.pack;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
            zip = new ZipFile(file.toFile(), StandardCharsets.UTF_8);
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
                new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
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
