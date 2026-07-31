package net.nennneko5787.sweetcookie.core.format.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import net.nennneko5787.sweetcookie.core.format.diag.DiagnosticLog;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostics;
import net.nennneko5787.sweetcookie.core.format.diag.FormatDiagnostics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Safe extraction, SC-100 §3.
 *
 * <p>Every limit here aborts the offending <b>pack</b> and leaves the rest of a load alone, so each
 * test asserts both halves: the pack is refused, and the reason is reported with a code the user can
 * search for.
 */
@ProvesSpec("SC-100")
class PackArchivesTest {

    private static final PackSource SOURCE = PackSource.of("test.mcpack", PackSource.Kind.MCPACK);

    private record Attempt(Optional<PackVfs> vfs, DiagnosticLog log) {
        boolean refused() {
            return vfs.isEmpty();
        }

        boolean reported(int code) {
            return !log.withCode(code).isEmpty();
        }
    }

    private static Attempt openBytes(byte[] archive, ExtractionLimits limits) {
        Diagnostics into = new Diagnostics();
        Optional<PackVfs> vfs = PackArchives.openZipBytes(archive, SOURCE, limits, into);
        return new Attempt(vfs, into.snapshot());
    }

    @Test
    @ProvesSpec("SC-100")
    void readsAnOrdinaryArchive() throws IOException {
        Attempt attempt = openBytes(TestArchives.zip()
                .with("manifest.json", "{}")
                .with("textures/blocks/a.png", "png")
                .bytes(), ExtractionLimits.DEFAULT);

        PackVfs vfs = attempt.vfs().orElseThrow();
        assertEquals("{}", new String(vfs.read("manifest.json").orElseThrow().read(),
                StandardCharsets.UTF_8));
        assertTrue(vfs.exists("TEXTURES/BLOCKS/A.PNG"));
        assertTrue(attempt.log().isEmpty());
    }

    @Test
    @ProvesSpec("SC-100")
    void refusesZipSlip() {
        Attempt attempt = openBytes(TestArchives.zip()
                .with("manifest.json", "{}")
                .with("../../evil.sh", "rm -rf")
                .bytes(), ExtractionLimits.DEFAULT);

        assertTrue(attempt.refused());
        assertTrue(attempt.reported(FormatDiagnostics.ENTRY_PATH_ESCAPES.code()));
    }

    @Test
    @ProvesSpec("SC-100")
    void refusesTooManyEntries() {
        TestArchives archive = TestArchives.zip();
        for (int i = 0; i < 10; i++) {
            archive.with("f" + i + ".json", "{}");
        }
        Attempt attempt = openBytes(archive.bytes(),
                new ExtractionLimits(1 << 20, 200, 4, 3, 1 << 20, 512));

        assertTrue(attempt.refused());
        assertTrue(attempt.reported(FormatDiagnostics.ARCHIVE_TOO_MANY_ENTRIES.code()));
    }

    @Test
    @ProvesSpec("SC-100")
    void refusesAnOversizedEntryEvenWhenItsHeaderLies() {
        // A streamed entry declares no size at all, so the only trustworthy bound is the count of
        // bytes that actually arrive. This is the check a lying central directory cannot buy past.
        Attempt attempt = openBytes(TestArchives.zip()
                .with("big.bin", new byte[4096])
                .bytes(), new ExtractionLimits(1 << 20, 200, 100, 3, 1024, 512));

        assertTrue(attempt.refused());
        assertTrue(attempt.reported(FormatDiagnostics.ENTRY_TOO_LARGE.code()));
    }

    @Test
    @ProvesSpec("SC-100")
    void refusesAnArchiveOverTheTotalSizeLimit() {
        Attempt attempt = openBytes(TestArchives.zip()
                .with("a.bin", new byte[600])
                .with("b.bin", new byte[600])
                .bytes(), new ExtractionLimits(1000, 200, 100, 3, 1 << 20, 512));

        assertTrue(attempt.refused());
        assertTrue(attempt.reported(FormatDiagnostics.ARCHIVE_TOO_LARGE.code()));
    }

    @Test
    @ProvesSpec("SC-100")
    void warnsOnACaseCollisionAndKeepsTheFirstEntry() throws IOException {
        // Not a refusal: the pack works with the first entry winning, and Bedrock loads it.
        Attempt attempt = openBytes(TestArchives.zip()
                .with("Foo.json", "first")
                .with("foo.json", "second")
                .bytes(), ExtractionLimits.DEFAULT);

        assertFalse(attempt.refused());
        assertTrue(attempt.reported(FormatDiagnostics.ENTRY_CASE_COLLISION.code()));
        assertEquals("first", new String(
                attempt.vfs().orElseThrow().read("foo.json").orElseThrow().read(),
                StandardCharsets.UTF_8));
    }

    @Test
    @ProvesSpec("SC-100")
    void refusesACompressionBombByItsDeclaredRatio(@TempDir Path dir) {
        // A megabyte of zeroes compresses to about a kilobyte. The declared ratio is the cheap
        // first filter; the per-file cap is the one that holds when the header lies.
        Path file = TestArchives.zip().with("bomb.bin", new byte[1 << 20])
                .writeTo(dir.resolve("bomb.mcpack"));

        Diagnostics into = new Diagnostics();
        Optional<PackVfs> vfs = PackArchives.openZip(
                file, SOURCE, new ExtractionLimits(1L << 30, 200, 100, 3, 1L << 30, 512), into);

        assertTrue(vfs.isEmpty());
        assertFalse(into.snapshot().withCode(FormatDiagnostics.ARCHIVE_TOO_LARGE.code()).isEmpty());
    }

    @Test
    @ProvesSpec("SC-100")
    void readsAZipOnDiskLazilyAndReleasesItOnClose(@TempDir Path dir) throws IOException {
        Path file = TestArchives.zip()
                .with("manifest.json", "{}")
                .writeTo(dir.resolve("pack.mcpack"));

        Diagnostics into = new Diagnostics();
        PackVfs vfs = PackArchives.openZip(file, SOURCE, ExtractionLimits.DEFAULT, into)
                .orElseThrow();
        assertEquals("{}", new String(vfs.read("manifest.json").orElseThrow().read(),
                StandardCharsets.UTF_8));
        vfs.close();

        // SC-100 §12: on Windows an open handle stops the author replacing the file they are
        // editing, which is the single most common thing an author does.
        Files.delete(file);
        assertFalse(Files.exists(file));
    }

    @Test
    @ProvesSpec("SC-100")
    void readsADirectoryInAStableOrder(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("textures/blocks"));
        Files.writeString(dir.resolve("manifest.json"), "{}");
        Files.writeString(dir.resolve("textures/blocks/z.png"), "z");
        Files.writeString(dir.resolve("textures/blocks/a.png"), "a");

        Diagnostics into = new Diagnostics();
        PackVfs vfs = PackArchives.openDirectory(
                dir, PackSource.of(dir.toString(), PackSource.Kind.DIRECTORY),
                ExtractionLimits.DEFAULT, into).orElseThrow();

        // A directory has no declared order, so one is imposed: two machines with different
        // filesystem enumeration orders must produce the same load order (SC-000 §9).
        assertEquals(
                java.util.List.of("manifest.json", "textures/blocks/a.png", "textures/blocks/z.png"),
                vfs.paths().toList());
    }
}
