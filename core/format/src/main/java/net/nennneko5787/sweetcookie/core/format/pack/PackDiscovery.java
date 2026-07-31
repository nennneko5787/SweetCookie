package net.nennneko5787.sweetcookie.core.format.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostics;
import net.nennneko5787.sweetcookie.core.format.diag.FormatDiagnostics;
import net.nennneko5787.sweetcookie.core.format.value.PackId;
import net.nennneko5787.sweetcookie.core.format.value.Provenance;

/**
 * Finds the packs inside a container. SC-100 §2.
 *
 * <p>Discovery searches recursively for {@code manifest.json} <b>at any depth</b> rather than
 * assuming a layout, because {@code .mcaddon} nesting is not normalised in practice: real add-ons
 * put packs at the root, in one subdirectory each, inside nested {@code .mcpack} files, or in a
 * mixture of all three within one file.
 *
 * <p>The outermost {@code manifest.json} on any path wins. An inner one is {@code SCE-1010} and is
 * ignored — otherwise a pack that ships a sample manifest as documentation, which several popular
 * ones do, would be detected as two packs, one of them nonsense.
 */
@SpecImpl("SC-100")
public final class PackDiscovery {

    /** A pack root, with the container VFS it was found in. */
    public record Found(PackVfs vfs, PackSource source, PackVfs owner) {
    }

    /** The result of scanning one container: what was found, and what must be closed. */
    public record Result(List<Found> packs, List<PackVfs> containers) {
    }

    private static final Set<String> ARCHIVE_EXTENSIONS =
            Set.of("mcpack", "mcaddon", "mcworld", "mctemplate", "zip");

    /** World data 0.x does not read. Named so it can be reported rather than silently skipped. */
    private static final Set<String> WORLD_DATA = Set.of("level.dat", "level.dat_old", "levelname.txt");

    private PackDiscovery() {
    }

    /** Scans one file or directory. */
    public static Result scan(Path path, ExtractionLimits limits, Diagnostics into) {
        PackSource source = PackSource.of(path.toString(), kindOf(path));
        Optional<PackVfs> container = open(path, source, limits, into);
        if (container.isEmpty()) {
            return new Result(List.of(), List.of());
        }
        List<Found> found = new ArrayList<>();
        List<PackVfs> containers = new ArrayList<>();
        containers.add(container.get());
        collect(container.get(), source, limits, into, found, containers);
        return new Result(found, containers);
    }

    private static Optional<PackVfs> open(
            Path path, PackSource source, ExtractionLimits limits, Diagnostics into) {
        if (Files.isDirectory(path)) {
            return PackArchives.openDirectory(path, source, limits, into);
        }
        if (!Files.isRegularFile(path)) {
            into.report(FormatDiagnostics.MANIFEST_UNUSABLE.at(
                    Provenance.file(PackId.NONE, path.toString()), "not a file or directory"));
            return Optional.empty();
        }
        return PackArchives.openZip(path, source, limits, into);
    }

    private static void collect(
            PackVfs vfs,
            PackSource source,
            ExtractionLimits limits,
            Diagnostics into,
            List<Found> found,
            List<PackVfs> containers) {

        Provenance where = Provenance.file(PackId.NONE, source.toString());

        // Manifest directories, shallowest first, so that the outermost wins by construction.
        List<String> manifestDirs = vfs.paths()
                .filter(path -> VfsPath.fileName(path).equalsIgnoreCase("manifest.json"))
                .map(VfsPath::parent)
                .sorted((a, b) -> {
                    int byDepth = Integer.compare(depth(a), depth(b));
                    return byDepth != 0 ? byDepth : a.compareTo(b);
                })
                .toList();

        Set<String> accepted = new LinkedHashSet<>();
        for (String dir : manifestDirs) {
            String key = VfsPath.key(dir);
            boolean inner = accepted.stream().anyMatch(outer -> VfsPath.isUnder(key, outer));
            if (inner) {
                into.report(FormatDiagnostics.NESTED_MANIFEST_IGNORED.at(where, dir));
                continue;
            }
            accepted.add(key);
            found.add(new Found(vfs.rooted(dir), source.at(dir), vfs));
        }

        if (source.kind() == PackSource.Kind.MCWORLD) {
            reportWorldData(vfs, where, into);
        }

        // Nested archives. Depth is bounded because an archive that contains itself is a cheap way
        // to spend the whole heap, and because nothing legitimate goes deeper than mcaddon/mcpack.
        List<String> nested = vfs.paths()
                .filter(path -> ARCHIVE_EXTENSIONS.contains(VfsPath.extension(path)))
                .filter(path -> accepted.stream().noneMatch(dir -> VfsPath.isUnder(VfsPath.key(path), dir)))
                .toList();
        for (String archive : nested) {
            if (source.nestingDepth() + 1 > limits.maxNestingDepth()) {
                into.report(FormatDiagnostics.ARCHIVE_TOO_DEEP.at(where, archive));
                continue;
            }
            Optional<ByteSource> bytes = vfs.read(archive);
            if (bytes.isEmpty()) {
                continue;
            }
            byte[] content;
            try {
                content = bytes.get().read();
            } catch (IOException e) {
                into.report(FormatDiagnostics.MANIFEST_UNUSABLE.at(where, archive, e.toString()));
                continue;
            }
            PackSource inner = source.nested(archive, kindOfExtension(VfsPath.extension(archive)));
            Optional<PackVfs> innerVfs =
                    PackArchives.openZipBytes(content, inner, limits, into);
            if (innerVfs.isEmpty()) {
                continue;
            }
            containers.add(innerVfs.get());
            collect(innerVfs.get(), inner, limits, into, found, containers);
        }
    }

    /**
     * Reports that a world container's own data is being skipped.
     *
     * <p>Informational, not an error: importing a Bedrock world means reading LevelDB and a
     * different chunk format, which is a separate project. Saying so beats a user concluding that
     * their {@code .mcworld} failed to load when its packs loaded fine.
     */
    private static void reportWorldData(PackVfs vfs, Provenance where, Diagnostics into) {
        boolean hasWorldData = vfs.paths().anyMatch(path -> {
            String name = VfsPath.fileName(path).toLowerCase(java.util.Locale.ROOT);
            return WORLD_DATA.contains(name) || VfsPath.key(path).startsWith("db/");
        });
        if (hasWorldData) {
            into.report(FormatDiagnostics.WORLD_DATA_SKIPPED.at(where));
        }
    }

    private static int depth(String path) {
        if (path.isEmpty()) {
            return 0;
        }
        return (int) path.chars().filter(c -> c == '/').count() + 1;
    }

    private static PackSource.Kind kindOf(Path path) {
        if (Files.isDirectory(path)) {
            return PackSource.Kind.DIRECTORY;
        }
        return kindOfExtension(VfsPath.extension(path.getFileName().toString()));
    }

    private static PackSource.Kind kindOfExtension(String extension) {
        return switch (extension) {
            case "mcpack" -> PackSource.Kind.MCPACK;
            case "mcworld", "mctemplate" -> PackSource.Kind.MCWORLD;
            // A .zip is an .mcpack when it has a root manifest and an .mcaddon otherwise (SC-100
            // §2). Discovery finds manifests at any depth, so the distinction changes nothing here
            // and the conservative label is the one that does not promise a root manifest.
            default -> PackSource.Kind.MCADDON;
        };
    }
}
