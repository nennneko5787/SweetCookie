package net.nennneko5787.sweetcookie.core.format.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import org.junit.jupiter.api.Test;

/** The virtual file system, SC-100 §9, and the subpack overlay, §7. */
@ProvesSpec("SC-100")
class VfsTest {

    private static IndexedVfs vfs(String... paths) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String path : paths) {
            files.put(path, path.getBytes(StandardCharsets.UTF_8));
        }
        return IndexedVfs.of(files);
    }

    @Test
    @ProvesSpec("SC-100")
    void looksUpCaseInsensitively() throws IOException {
        // Real packs reference Textures/Blocks/Foo.PNG for textures/blocks/foo.png. A
        // case-sensitive lookup loses those textures on Linux servers only, which is the worst
        // possible shape for a bug.
        IndexedVfs vfs = vfs("textures/blocks/foo.png");
        assertTrue(vfs.exists("Textures/Blocks/FOO.PNG"));
        assertTrue(vfs.exists("textures\\blocks\\foo.png"));
        assertEquals("textures/blocks/foo.png",
                new String(vfs.read("TEXTURES/blocks/Foo.png").orElseThrow().read(),
                        StandardCharsets.UTF_8));
        assertFalse(vfs.exists("textures/blocks/bar.png"));
    }

    @Test
    @ProvesSpec("SC-100")
    void keepsTheSpellingTheArchiveUsed() {
        // Diagnostics must quote what the author wrote, not what we folded it to.
        assertEquals("Textures/Blocks/Foo.PNG",
                vfs("Textures/Blocks/Foo.PNG").entry("textures/blocks/foo.png")
                        .orElseThrow().path());
    }

    @Test
    @ProvesSpec("SC-100")
    void listsImmediateChildrenOnlyAndWalksRecursively() {
        IndexedVfs vfs = vfs(
                "manifest.json",
                "textures/terrain_texture.json",
                "textures/blocks/a.png",
                "textures/blocks/b.png",
                "textures/items/c.png");

        assertEquals(List.of("textures/terrain_texture.json", "textures/blocks", "textures/items"),
                vfs.list("textures"));
        assertEquals(List.of("manifest.json", "textures"), vfs.list(""));
        assertEquals(2, vfs.walk("textures/blocks").count());
        assertEquals(4, vfs.walk("textures").count());
        assertEquals(5, vfs.walk("").count());
    }

    @Test
    @ProvesSpec("SC-100")
    void iteratesInArchiveOrder() {
        // SC-110 §10: the block ledger depends on a deterministic walk, and a non-deterministic
        // ledger corrupts worlds rather than producing an odd diff.
        assertEquals(List.of("z.json", "a.json", "m.json"),
                vfs("z.json", "a.json", "m.json").paths().toList());
    }

    @Test
    @ProvesSpec("SC-100")
    void rootedViewsRebaseWithoutCopying() {
        IndexedVfs vfs = vfs("subpacks/hd/textures/a.png", "textures/a.png");
        PackVfs hd = vfs.rooted("subpacks/hd");
        assertTrue(hd.exists("textures/a.png"));
        assertFalse(hd.exists("subpacks/hd/textures/a.png"));
        assertEquals(List.of("textures/a.png"), hd.walk("").toList());
    }

    @Test
    @ProvesSpec("SC-100")
    void aLayerOverridesTheOneBelowIt() throws IOException {
        PackVfs base = IndexedVfs.of(Map.of(
                "textures/a.png", "base-a".getBytes(StandardCharsets.UTF_8),
                "textures/b.png", "base-b".getBytes(StandardCharsets.UTF_8)));
        PackVfs overlay = IndexedVfs.of(Map.of(
                "textures/a.png", "hd-a".getBytes(StandardCharsets.UTF_8)));
        PackVfs layered = LayeredVfs.over(overlay, base);

        assertEquals("hd-a", new String(layered.read("textures/a.png").orElseThrow().read(),
                StandardCharsets.UTF_8));
        assertEquals("base-b", new String(layered.read("textures/b.png").orElseThrow().read(),
                StandardCharsets.UTF_8));
    }

    @Test
    @ProvesSpec("SC-100")
    void aLayeredWalkCountsAnOverriddenFileOnce() {
        // A subpack shipping Textures/A.PNG over the root's textures/a.png is one file. Listing it
        // twice makes an asset-pipeline walk read and upload it twice.
        PackVfs layered = LayeredVfs.over(
                IndexedVfs.of(Map.of("Textures/A.PNG", new byte[0])),
                IndexedVfs.of(Map.of("textures/a.png", new byte[0], "textures/b.png", new byte[0])));
        assertEquals(List.of("Textures/A.PNG", "textures/b.png"), layered.walk("").toList());
    }

    @Test
    @ProvesSpec("SC-100")
    void subpackSelectionTakesTheHighestTierUnderTheCeiling() {
        List<net.nennneko5787.sweetcookie.core.format.manifest.SubpackDecl> subpacks = List.of(
                new net.nennneko5787.sweetcookie.core.format.manifest.SubpackDecl("sd", "SD", 0),
                new net.nennneko5787.sweetcookie.core.format.manifest.SubpackDecl("hd", "HD", 4),
                new net.nennneko5787.sweetcookie.core.format.manifest.SubpackDecl("uhd", "UHD", 8));

        assertEquals("uhd", SubpackSelection.choose(subpacks, java.util.OptionalInt.empty())
                .selected().orElseThrow().folderName());
        assertEquals("hd", SubpackSelection.choose(subpacks, java.util.OptionalInt.of(5))
                .selected().orElseThrow().folderName());
        assertEquals("sd", SubpackSelection.choose(subpacks, java.util.OptionalInt.of(0))
                .selected().orElseThrow().folderName());
        assertTrue(SubpackSelection.choose(List.of(), java.util.OptionalInt.of(4))
                .selected().isEmpty());
    }

    @Test
    @ProvesSpec("SC-100")
    void anAppliedSubpackShadowsThePackRoot() throws IOException {
        IndexedVfs root = IndexedVfs.of(Map.of(
                "textures/a.png", "root".getBytes(StandardCharsets.UTF_8),
                "subpacks/hd/textures/a.png", "hd".getBytes(StandardCharsets.UTF_8)));
        SubpackSelection selection = SubpackSelection.choose(
                List.of(new net.nennneko5787.sweetcookie.core.format.manifest.SubpackDecl(
                        "hd", "HD", 4)),
                java.util.OptionalInt.empty());

        assertEquals("hd", new String(
                selection.applyTo(root).read("textures/a.png").orElseThrow().read(),
                StandardCharsets.UTF_8));
    }
}
