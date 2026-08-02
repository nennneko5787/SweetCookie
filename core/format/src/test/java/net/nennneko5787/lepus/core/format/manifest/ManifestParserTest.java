package net.nennneko5787.lepus.core.format.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import net.nennneko5787.lepus.core.format.diag.DiagnosticLog;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.diag.FormatDiagnostics;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.value.BedrockVersion;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.Provenance;
import net.nennneko5787.lepus.core.format.value.SemanticVersion;
import org.junit.jupiter.api.Test;

/** {@code manifest.json} in all three format versions. SC-100 §4. */
@ProvesSpec("SC-100")
class ManifestParserTest {

    private static final String UUID_A = "11111111-2222-3333-4444-555555555555";
    private static final Provenance WHERE = Provenance.file(PackId.NONE, "manifest.json");

    private Diagnostics diagnostics = new Diagnostics();

    private Optional<Manifest> parse(String json) {
        diagnostics = new Diagnostics();
        return ManifestParser.parse(
                Json.parse(json).asObject().orElseThrow(), WHERE, diagnostics);
    }

    private DiagnosticLog log() {
        return diagnostics.snapshot();
    }

    private boolean reported(int code) {
        return !log().withCode(code).isEmpty();
    }

    @Test
    @ProvesSpec("SC-100")
    void parsesTheCommonFormatVersionTwoShape() {
        Manifest manifest = parse("""
                {
                  "format_version": 2,
                  "header": {
                    "uuid": "%s",
                    "name": "pack.name",
                    "description": "pack.description",
                    "version": [1, 2, 3],
                    "min_engine_version": [1, 21, 0]
                  },
                  "modules": [
                    { "type": "resources", "uuid": "%s", "version": [1, 2, 3] },
                    { "type": "data",      "uuid": "%s", "version": [1, 2, 3] }
                  ]
                }
                """.formatted(UUID_A, UUID_A, UUID_A)).orElseThrow();

        assertEquals(2, manifest.formatVersion());
        assertEquals(PackId.parse(UUID_A).orElseThrow(), manifest.header().id());
        assertEquals(SemanticVersion.of(1, 2, 3), manifest.version());
        assertEquals(BedrockVersion.of(1, 21, 0), manifest.header().minEngineVersion());
        // One pack providing both halves is ONE pack, not two: otherwise it gets two identities in
        // the ledger and the user can disable half of it.
        assertTrue(manifest.hasBehavior());
        assertTrue(manifest.hasResources());
        assertTrue(log().isEmpty());
    }

    @Test
    @ProvesSpec("SC-100")
    void acceptsEitherVersionShapeWhateverTheFormatVersionClaims() {
        // Format 1 and 2 write arrays, format 3 writes SemVer strings, and real manifests mix them.
        // Trusting the declaration would reject packs that work.
        Manifest manifest = parse("""
                {
                  "format_version": 2,
                  "header": { "uuid": "%s", "version": "1.2.3-beta.1",
                              "min_engine_version": "1.21.0" },
                  "modules": [ { "type": "data", "uuid": "%s", "version": [1, 0, 0] } ]
                }
                """.formatted(UUID_A, UUID_A)).orElseThrow();

        assertEquals("1.2.3-beta.1", manifest.version().toString());
        assertEquals(BedrockVersion.of(1, 21, 0), manifest.header().minEngineVersion());
    }

    @Test
    @ProvesSpec("SC-100")
    void acceptsAnUnknownFormatVersionAsIfItWereTwo() {
        // Mojang's history here is additive. Refusing would make the mod useless the day Bedrock
        // ships a format_version 4.
        Manifest manifest = parse("""
                {
                  "format_version": 4,
                  "header": { "uuid": "%s", "version": [1, 0, 0], "min_engine_version": [1, 21, 0] },
                  "modules": [ { "type": "data", "uuid": "%s", "version": [1, 0, 0] } ]
                }
                """.formatted(UUID_A, UUID_A)).orElseThrow();

        assertEquals(2, manifest.formatVersion());
        assertTrue(reported(FormatDiagnostics.MANIFEST_UNKNOWN_FORMAT_VERSION.code()));
    }

    @Test
    @ProvesSpec("SC-100")
    void derivesAStableIdentityFromAMalformedUuid() {
        Manifest first = parse("""
                {
                  "header": { "uuid": "not-a-uuid", "version": [1, 0, 0],
                              "min_engine_version": [1, 21, 0] },
                  "modules": [ { "type": "data", "uuid": "x", "version": [1, 0, 0] } ]
                }
                """).orElseThrow();
        assertTrue(reported(FormatDiagnostics.MANIFEST_UUID_MALFORMED.code()));
        assertEquals(PackId.derived("not-a-uuid"), first.header().id());

        // Stable across loads, because the block ledger keys on it.
        Manifest again = parse("""
                {
                  "header": { "uuid": "not-a-uuid", "version": [1, 0, 0],
                              "min_engine_version": [1, 21, 0] },
                  "modules": [ { "type": "data", "uuid": "x", "version": [1, 0, 0] } ]
                }
                """).orElseThrow();
        assertEquals(first.header().id(), again.header().id());
    }

    @Test
    @ProvesSpec("SC-100")
    void defaultsAnAbsentMinEngineVersion() {
        Manifest manifest = parse("""
                {
                  "header": { "uuid": "%s", "version": [1, 0, 0] },
                  "modules": [ { "type": "data", "uuid": "%s", "version": [1, 0, 0] } ]
                }
                """.formatted(UUID_A, UUID_A)).orElseThrow();

        assertEquals(PackHeader.ASSUMED_MIN_ENGINE_VERSION, manifest.header().minEngineVersion());
        assertTrue(reported(FormatDiagnostics.MANIFEST_NO_MIN_ENGINE_VERSION.code()));
    }

    @Test
    @ProvesSpec("SC-100")
    void toleratesAVersionArrayOfTheWrongLength() {
        Manifest manifest = parse("""
                {
                  "header": { "uuid": "%s", "version": [1, 2], "min_engine_version": [1, 21, 0] },
                  "modules": [ { "type": "data", "uuid": "%s", "version": [1, 0, 0] } ]
                }
                """.formatted(UUID_A, UUID_A)).orElseThrow();

        assertEquals(SemanticVersion.of(1, 2, 0), manifest.version());
        assertTrue(reported(FormatDiagnostics.MANIFEST_VERSION_MALFORMED.code()));
    }

    @Test
    @ProvesSpec("SC-100")
    void recognisesEveryModuleTypeAndReportsTheOnesItCannot() {
        Manifest manifest = parse("""
                {
                  "header": { "uuid": "%s", "version": [1, 0, 0], "min_engine_version": [1, 21, 0] },
                  "modules": [
                    { "type": "client_data", "uuid": "%s", "version": [1, 0, 0] },
                    { "type": "skin_pack",   "uuid": "%s", "version": [1, 0, 0] },
                    { "type": "hologram",    "uuid": "%s", "version": [1, 0, 0] }
                  ]
                }
                """.formatted(UUID_A, UUID_A, UUID_A, UUID_A)).orElseThrow();

        assertEquals(ModuleType.CLIENT_DATA, manifest.modules().get(0).type());
        assertTrue(manifest.hasBehavior()); // client_data counts as data
        assertEquals(ModuleType.SKIN_PACK, manifest.modules().get(1).type());
        assertEquals(ModuleType.UNKNOWN, manifest.modules().get(2).type());
        assertEquals("hologram", manifest.modules().get(2).declaredType());
        assertTrue(reported(FormatDiagnostics.MANIFEST_CLIENT_DATA_MODULE.code()));
        // A module type nobody recognises is how a pack contributes content that never appears,
        // with nothing anywhere saying so.
        assertTrue(reported(FormatDiagnostics.MANIFEST_UNKNOWN_MODULE_TYPE.code()));
    }

    @Test
    @ProvesSpec("SC-100")
    void defaultsAScriptModuleEntryPoint() {
        // `entry` is absent from Mojang's published field table and universal in practice.
        Manifest manifest = parse("""
                {
                  "header": { "uuid": "%s", "version": [1, 0, 0], "min_engine_version": [1, 21, 0] },
                  "modules": [
                    { "type": "script", "language": "javascript", "uuid": "%s", "version": [1, 0, 0] }
                  ]
                }
                """.formatted(UUID_A, UUID_A)).orElseThrow();

        assertEquals(PackModule.DEFAULT_SCRIPT_ENTRY, manifest.modules().get(0).entry());
        assertEquals("javascript", manifest.modules().get(0).language());
        assertTrue(manifest.hasScripts());
        assertTrue(reported(FormatDiagnostics.MANIFEST_SCRIPT_ENTRY_DEFAULTED.code()));
    }

    @Test
    @ProvesSpec("SC-100")
    void parsesBothDependencyShapesAndDropsNeither() {
        Manifest manifest = parse("""
                {
                  "header": { "uuid": "%s", "version": [1, 0, 0], "min_engine_version": [1, 21, 0] },
                  "modules": [ { "type": "data", "uuid": "%s", "version": [1, 0, 0] } ],
                  "dependencies": [
                    { "uuid": "%s", "version": [1, 0, 0] },
                    { "module_name": "@minecraft/server", "version": "2.8.0" },
                    { "version": [1, 0, 0] }
                  ]
                }
                """.formatted(UUID_A, UUID_A, UUID_A)).orElseThrow();

        assertEquals(1, manifest.packDependencies().size());
        assertEquals(1, manifest.moduleDependencies().size());
        assertEquals("@minecraft/server", manifest.moduleDependencies().get(0).moduleName());
        assertTrue(reported(FormatDiagnostics.MANIFEST_DEPENDENCY_UNUSABLE.code()));
    }

    @Test
    @ProvesSpec("SC-100")
    void recordsCapabilitiesAndSubpacksAndMetadata() {
        Manifest manifest = parse("""
                {
                  "header": {
                    "uuid": "%s", "version": [1, 0, 0], "min_engine_version": [1, 21, 0],
                    "pack_scope": "world",
                    "subpacks": [
                      { "folder_name": "hd", "name": "HD Textures", "memory_tier": 4 },
                      { "folder_name": "sd", "name": "SD Textures", "memory_tier": 0 }
                    ]
                  },
                  "modules": [ { "type": "resources", "uuid": "%s", "version": [1, 0, 0] } ],
                  "capabilities": ["chemistry", "editorExtension", "holography"],
                  "metadata": {
                    "authors": ["someone"],
                    "license": "MIT",
                    "generated_with": { "bridge": ["1.0.0"] }
                  }
                }
                """.formatted(UUID_A, UUID_A)).orElseThrow();

        assertEquals(PackScope.WORLD, manifest.header().scope());
        assertEquals(List.of("hd", "sd"),
                manifest.header().subpacks().stream().map(SubpackDecl::folderName).toList());
        assertEquals("subpacks/hd", manifest.header().subpacks().get(0).path());
        assertEquals(2, manifest.capabilities().size());
        assertTrue(manifest.capabilities().contains(Capability.EDITOR_EXTENSION));
        assertEquals(java.util.Set.of("holography"), manifest.unknownCapabilities());
        assertEquals(List.of("someone"), manifest.metadata().authors());
        assertEquals(List.of("bridge 1.0.0"), manifest.metadata().generatedWith());
    }

    @Test
    @ProvesSpec("SC-100")
    void refusesOnlyWhenThereIsNoIdentityOrNoModules() {
        // The two cases where degrading is not an option: a pack with no identity cannot be tracked
        // in the ledger, and a pack with no modules has nothing to contribute.
        assertTrue(parse("""
                { "header": { "version": [1, 0, 0] },
                  "modules": [ { "type": "data", "uuid": "x", "version": [1, 0, 0] } ] }
                """).isEmpty());
        assertTrue(reported(FormatDiagnostics.MANIFEST_UNUSABLE.code()));

        assertTrue(parse("""
                { "header": { "uuid": "%s", "version": [1, 0, 0] }, "modules": [] }
                """.formatted(UUID_A)).isEmpty());
        assertTrue(reported(FormatDiagnostics.MANIFEST_UNUSABLE.code()));

        assertTrue(parse("{}").isEmpty());
    }

    @Test
    @ProvesSpec("SC-100")
    void keepsNameAndDescriptionRaw() {
        // They are frequently .lang keys. Resolving one here would need a locale that this layer has
        // no business knowing: a dedicated server and each of its clients want different ones.
        Manifest manifest = parse("""
                {
                  "header": { "uuid": "%s", "name": "pack.name", "version": [1, 0, 0],
                              "min_engine_version": [1, 21, 0] },
                  "modules": [ { "type": "data", "uuid": "%s", "version": [1, 0, 0] } ]
                }
                """.formatted(UUID_A, UUID_A)).orElseThrow();

        assertEquals("pack.name", manifest.header().name());
        assertFalse(manifest.header().name().isEmpty());
    }
}
