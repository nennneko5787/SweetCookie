package net.nennneko5787.sweetcookie.core.format.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import net.nennneko5787.sweetcookie.core.format.diag.FormatDiagnostics;
import net.nennneko5787.sweetcookie.core.format.value.PackId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End to end: files on disk to packs in resolved load order. SC-100. */
@ProvesSpec("SC-100")
class AddonLoaderTest {

    private static final String UUID_A = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String UUID_B = "bbbbbbbb-0000-0000-0000-000000000002";

    private static boolean reported(LoadedAddon addon, int code) {
        return !addon.diagnostics().withCode(code).isEmpty();
    }

    @Test
    @ProvesSpec("SC-100")
    void findsPacksAtAnyDepthInsideAnAddon(@TempDir Path dir) throws IOException {
        // .mcaddon nesting is not normalised in practice: real add-ons put packs at the root, one
        // per subdirectory, inside nested .mcpack files, or in a mixture of all three.
        Path addon = TestArchives.zip()
                .with("behavior/manifest.json", TestArchives.manifest(UUID_A, "BP", new int[]{1, 0, 0}))
                .with("behavior/entities/wizard.json", "{}")
                .with("deeply/nested/resources/manifest.json",
                        TestArchives.manifest(UUID_B, "RP", new int[]{1, 0, 0}))
                .writeTo(dir.resolve("wizardry.mcaddon"));

        try (LoadedAddon addonLoaded = AddonLoader.load(List.of(addon))) {
            assertEquals(2, addonLoaded.packs().size());
            LoadedPack bp = addonLoaded.byId(PackId.parse(UUID_A).orElseThrow()).orElseThrow();
            assertTrue(bp.vfs().exists("entities/wizard.json"));
            assertFalse(bp.vfs().exists("behavior/entities/wizard.json"));
        }
    }

    @Test
    @ProvesSpec("SC-100")
    void ignoresAManifestInsideAnAlreadyDiscoveredPack(@TempDir Path dir) throws IOException {
        // Several popular packs ship a sample manifest as documentation. Detecting it as a second
        // pack would give the user a pack made of nonsense.
        Path addon = TestArchives.zip()
                .with("manifest.json", TestArchives.manifest(UUID_A, "BP", new int[]{1, 0, 0}))
                .with("documentation/example/manifest.json",
                        TestArchives.manifest(UUID_B, "Example", new int[]{1, 0, 0}))
                .writeTo(dir.resolve("wizardry.mcaddon"));

        try (LoadedAddon addonLoaded = AddonLoader.load(List.of(addon))) {
            assertEquals(1, addonLoaded.packs().size());
            assertTrue(reported(addonLoaded, FormatDiagnostics.NESTED_MANIFEST_IGNORED.code()));
        }
    }

    @Test
    @ProvesSpec("SC-100")
    void opensPacksNestedAsArchives(@TempDir Path dir) throws IOException {
        byte[] inner = TestArchives.zip()
                .with("manifest.json", TestArchives.manifest(UUID_B, "Inner", new int[]{1, 0, 0}))
                .bytes();
        Path addon = TestArchives.zip()
                .with("behavior/manifest.json",
                        TestArchives.manifest(UUID_A, "Outer", new int[]{1, 0, 0}))
                .with("inner.mcpack", inner)
                .writeTo(dir.resolve("wizardry.mcaddon"));

        try (LoadedAddon addonLoaded = AddonLoader.load(List.of(addon))) {
            assertEquals(2, addonLoaded.packs().size());
            assertTrue(addonLoaded.byId(PackId.parse(UUID_B).orElseThrow()).isPresent());
        }
    }

    @Test
    @ProvesSpec("SC-100")
    void treatsAnArchiveInsideAPackAsContentRatherThanAsAPack(@TempDir Path dir)
            throws IOException {
        // The mirror of the nested-manifest rule. A pack that ships an example .mcpack — several
        // popular ones do, as documentation — is shipping a file, not declaring a second pack.
        Path pack = TestArchives.zip()
                .with("manifest.json", TestArchives.manifest(UUID_A, "Outer", new int[]{1, 0, 0}))
                .with("examples/sample.mcpack", TestArchives.zip()
                        .with("manifest.json",
                                TestArchives.manifest(UUID_B, "Sample", new int[]{1, 0, 0}))
                        .bytes())
                .writeTo(dir.resolve("wizardry.mcpack"));

        try (LoadedAddon addon = AddonLoader.load(List.of(pack))) {
            assertEquals(1, addon.packs().size());
            assertTrue(addon.packs().get(0).vfs().exists("examples/sample.mcpack"));
        }
    }

    @Test
    @ProvesSpec("SC-100")
    void loadsADirectoryOnDisk(@TempDir Path dir) throws IOException {
        Path pack = dir.resolve("wizardry_bp");
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("manifest.json"),
                TestArchives.manifest(UUID_A, "BP", new int[]{1, 0, 0}));

        try (LoadedAddon addon = AddonLoader.load(List.of(pack))) {
            assertEquals(1, addon.packs().size());
            assertEquals(PackSource.Kind.DIRECTORY, addon.packs().get(0).source().kind());
        }
    }

    @Test
    @ProvesSpec("SC-100")
    void ordersDeterministicallyBySourcePath(@TempDir Path dir) throws IOException {
        Path b = TestArchives.zip()
                .with("manifest.json", TestArchives.manifest(UUID_B, "B", new int[]{1, 0, 0}))
                .writeTo(dir.resolve("b.mcpack"));
        Path a = TestArchives.zip()
                .with("manifest.json", TestArchives.manifest(UUID_A, "A", new int[]{1, 0, 0}))
                .writeTo(dir.resolve("a.mcpack"));

        // Given in b, a order; resolved in a, b order, because load order comes from the sanitised
        // source path and never from the order the caller happened to hand them over.
        try (LoadedAddon addon = AddonLoader.load(List.of(b, a))) {
            assertEquals(List.of("A", "B"),
                    addon.packs().stream().map(p -> p.header().name()).toList());
            assertEquals(List.of(0, 1), addon.packs().stream().map(LoadedPack::loadOrder).toList());
        }
    }

    @Test
    @ProvesSpec("SC-100")
    void anExplicitlyOrderedPackOutranksAnUnlistedOne(@TempDir Path dir) throws IOException {
        Path a = TestArchives.zip()
                .with("manifest.json", TestArchives.manifest(UUID_A, "A", new int[]{1, 0, 0}))
                .writeTo(dir.resolve("a.mcpack"));
        Path b = TestArchives.zip()
                .with("manifest.json", TestArchives.manifest(UUID_B, "B", new int[]{1, 0, 0}))
                .writeTo(dir.resolve("b.mcpack"));

        LoadOptions options = LoadOptions.DEFAULT
                .withActivationOrder(List.of(PackId.parse(UUID_A).orElseThrow()));

        // A is listed in the world's activation file and B is not, so A wins despite sorting first
        // by path. SC-100 §5 was ambiguous about this and was amended to say so.
        try (LoadedAddon addon = AddonLoader.load(List.of(a, b), options)) {
            assertEquals(List.of("B", "A"),
                    addon.packs().stream().map(p -> p.header().name()).toList());
        }
    }

    @Test
    @ProvesSpec("SC-100")
    void keepsTheHighestVersionWhenOneUuidAppearsTwice(@TempDir Path dir) throws IOException {
        Path older = TestArchives.zip()
                .with("manifest.json", TestArchives.manifest(UUID_A, "old", new int[]{1, 0, 0}))
                .writeTo(dir.resolve("a-old.mcpack"));
        Path newer = TestArchives.zip()
                .with("manifest.json", TestArchives.manifest(UUID_A, "new", new int[]{2, 0, 0}))
                .writeTo(dir.resolve("b-new.mcpack"));

        try (LoadedAddon addon = AddonLoader.load(List.of(older, newer))) {
            assertEquals(1, addon.packs().size());
            assertEquals("new", addon.packs().get(0).header().name());
            assertTrue(reported(addon, FormatDiagnostics.PACK_DUPLICATE_VERSIONS.code()));
        }
    }

    @Test
    @ProvesSpec("SC-100")
    void keepsTheLaterPackWhenBothUuidAndVersionMatch(@TempDir Path dir) throws IOException {
        Path first = TestArchives.zip()
                .with("manifest.json", TestArchives.manifest(UUID_A, "first", new int[]{1, 0, 0}))
                .writeTo(dir.resolve("a.mcpack"));
        Path second = TestArchives.zip()
                .with("manifest.json", TestArchives.manifest(UUID_A, "second", new int[]{1, 0, 0}))
                .writeTo(dir.resolve("z.mcpack"));

        try (LoadedAddon addon = AddonLoader.load(List.of(first, second))) {
            assertEquals(1, addon.packs().size());
            assertEquals("second", addon.packs().get(0).header().name());
            assertTrue(reported(addon, FormatDiagnostics.PACK_DUPLICATE.code()));
        }
    }

    @Test
    @ProvesSpec("SC-100")
    void loadsAPackThatNeedsANewerEngineAnyway(@TempDir Path dir) throws IOException {
        // Refusing would make the mod useless the day Bedrock ships an update, and most such packs
        // work regardless.
        Path pack = TestArchives.zip().with("manifest.json", """
                {
                  "format_version": 2,
                  "header": { "uuid": "%s", "name": "future", "version": [1, 0, 0],
                              "min_engine_version": [9, 0, 0] },
                  "modules": [ { "type": "data", "uuid": "%s", "version": [1, 0, 0] } ]
                }
                """.formatted(UUID_A, UUID_A)).writeTo(dir.resolve("future.mcpack"));

        try (LoadedAddon addon = AddonLoader.load(List.of(pack))) {
            assertEquals(1, addon.packs().size());
            assertTrue(reported(addon, FormatDiagnostics.ENGINE_VERSION_AHEAD.code()));
        }
    }

    @Test
    @ProvesSpec("SC-100")
    void warnsAboutDependenciesWithoutRefusingAnything(@TempDir Path dir) throws IOException {
        Path pack = TestArchives.zip().with("manifest.json", """
                {
                  "format_version": 2,
                  "header": { "uuid": "%s", "name": "BP", "version": [1, 0, 0],
                              "min_engine_version": [1, 21, 0] },
                  "modules": [ { "type": "data", "uuid": "%s", "version": [1, 0, 0] } ],
                  "dependencies": [
                    { "uuid": "%s", "version": [1, 0, 0] },
                    { "module_name": "@minecraft/server-net", "version": "1.0.0" }
                  ]
                }
                """.formatted(UUID_A, UUID_A, UUID_B)).writeTo(dir.resolve("bp.mcpack"));

        try (LoadedAddon addon = AddonLoader.load(List.of(pack))) {
            assertEquals(1, addon.packs().size());
            assertTrue(reported(addon, FormatDiagnostics.DEPENDENCY_MISSING.code()));
            assertTrue(reported(addon, FormatDiagnostics.SCRIPT_MODULE_UNSUPPORTED.code()));
            assertFalse(addon.diagnostics().hasErrors());
        }
    }

    @Test
    @ProvesSpec("SC-100")
    void toleratesADependencyCycle(@TempDir Path dir) throws IOException {
        // A behavior pack and its paired resource pack depending on each other is the common case,
        // so the graph is never topologically sorted in a way that fails on one.
        Path bp = TestArchives.zip().with("manifest.json", dependent(UUID_A, "BP", UUID_B))
                .writeTo(dir.resolve("bp.mcpack"));
        Path rp = TestArchives.zip().with("manifest.json", dependent(UUID_B, "RP", UUID_A))
                .writeTo(dir.resolve("rp.mcpack"));

        try (LoadedAddon addon = AddonLoader.load(List.of(bp, rp))) {
            assertEquals(2, addon.packs().size());
            assertFalse(reported(addon, FormatDiagnostics.DEPENDENCY_MISSING.code()));
        }
    }

    @Test
    @ProvesSpec("SC-100")
    void oneBrokenPackDoesNotTakeTheOthersWithIt(@TempDir Path dir) throws IOException {
        Path good = TestArchives.zip()
                .with("manifest.json", TestArchives.manifest(UUID_A, "good", new int[]{1, 0, 0}))
                .writeTo(dir.resolve("a-good.mcpack"));
        Path brokenJson = TestArchives.zip()
                .with("manifest.json", "{ this is not json")
                .writeTo(dir.resolve("b-broken.mcpack"));
        Path noUuid = TestArchives.zip()
                .with("manifest.json", "{\"header\":{},\"modules\":[]}")
                .writeTo(dir.resolve("c-nouuid.mcpack"));

        try (LoadedAddon addon = AddonLoader.load(List.of(good, brokenJson, noUuid))) {
            assertEquals(1, addon.packs().size());
            assertEquals("good", addon.packs().get(0).header().name());
            assertTrue(reported(addon, FormatDiagnostics.JSON_MALFORMED.code()));
            assertTrue(reported(addon, FormatDiagnostics.MANIFEST_UNUSABLE.code()));
        }
    }

    @Test
    @ProvesSpec("SC-100")
    void appliesTheSelectedSubpackAndReadsTexts(@TempDir Path dir) throws IOException {
        Path pack = TestArchives.zip()
                .with("manifest.json", """
                        {
                          "format_version": 2,
                          "header": {
                            "uuid": "%s", "name": "pack.name", "version": [1, 0, 0],
                            "min_engine_version": [1, 21, 0],
                            "subpacks": [ { "folder_name": "hd", "name": "HD", "memory_tier": 4 } ]
                          },
                          "modules": [ { "type": "resources", "uuid": "%s", "version": [1, 0, 0] } ]
                        }
                        """.formatted(UUID_A, UUID_A))
                .with("texts/en_US.lang", "pack.name=Wizardry")
                .with("textures/a.png", "root")
                .with("subpacks/hd/textures/a.png", "hd")
                .writeTo(dir.resolve("rp.mcpack"));

        try (LoadedAddon addon = AddonLoader.load(List.of(pack))) {
            LoadedPack loaded = addon.packs().get(0);
            assertEquals("hd", loaded.subpacks().selected().orElseThrow().folderName());
            assertEquals("hd", new String(
                    loaded.vfs().read("textures/a.png").orElseThrow().read(),
                    java.nio.charset.StandardCharsets.UTF_8));
            assertEquals("Wizardry", loaded.displayName("en_US"));
        }
    }

    @Test
    @ProvesSpec("SC-100")
    void reportsEveryDeclaredCapabilitySeparately(@TempDir Path dir) throws IOException {
        Path pack = TestArchives.zip().with("manifest.json", """
                {
                  "format_version": 2,
                  "header": { "uuid": "%s", "name": "c", "version": [1, 0, 0],
                              "min_engine_version": [1, 21, 0] },
                  "modules": [ { "type": "data", "uuid": "%s", "version": [1, 0, 0] } ],
                  "capabilities": ["chemistry", "raytraced"]
                }
                """.formatted(UUID_A, UUID_A)).writeTo(dir.resolve("c.mcpack"));

        try (LoadedAddon addon = AddonLoader.load(List.of(pack))) {
            // Each is independently unsupported; collapsing them would hide which one a player is
            // missing.
            assertEquals(2,
                    addon.diagnostics().withCode(FormatDiagnostics.CAPABILITY_UNSUPPORTED.code())
                            .size());
        }
    }

    private static String dependent(String uuid, String name, String dependsOn) {
        return """
                {
                  "format_version": 2,
                  "header": { "uuid": "%s", "name": "%s", "version": [1, 0, 0],
                              "min_engine_version": [1, 21, 0] },
                  "modules": [ { "type": "data", "uuid": "%s", "version": [1, 0, 0] } ],
                  "dependencies": [ { "uuid": "%s", "version": [1, 0, 0] } ]
                }
                """.formatted(uuid, name, uuid, dependsOn);
    }
}
