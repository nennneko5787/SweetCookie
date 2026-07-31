package net.nennneko5787.sweetcookie.core.format.manifest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostics;
import net.nennneko5787.sweetcookie.core.format.diag.FormatDiagnostics;
import net.nennneko5787.sweetcookie.core.format.json.JsonArray;
import net.nennneko5787.sweetcookie.core.format.json.JsonNumber;
import net.nennneko5787.sweetcookie.core.format.json.JsonObject;
import net.nennneko5787.sweetcookie.core.format.json.JsonPointer;
import net.nennneko5787.sweetcookie.core.format.json.JsonValue;
import net.nennneko5787.sweetcookie.core.format.value.BedrockVersion;
import net.nennneko5787.sweetcookie.core.format.value.PackId;
import net.nennneko5787.sweetcookie.core.format.value.Provenance;
import net.nennneko5787.sweetcookie.core.format.value.SemanticVersion;

/**
 * Parses {@code manifest.json} in all three format versions into one shape. SC-100 §4.
 *
 * <p>Only two things can stop a manifest parsing: no usable {@code header.uuid}, and no usable
 * {@code modules}. Everything else degrades — a malformed UUID becomes a derived one, a malformed
 * version becomes what its parts say, an unknown module type is recorded and reported. That is
 * constitution rule 1 applied at the one place where refusing is genuinely an option, and the reason
 * to refuse in those two cases is that a pack with no identity cannot be tracked in the ledger and a
 * pack with no modules has nothing to contribute.
 *
 * <p><b>The declared {@code format_version} does not select the parser.</b> Format 1 and 2 write
 * versions as {@code [major, minor, patch]} and format 3 writes SemVer strings, but real manifests
 * mix them, so both shapes are accepted regardless of what the file claims. An unrecognised
 * {@code format_version} is {@code SCE-1020} and parsing continues as version 2 — Mojang's history
 * here is of additive change, and refusing would make the mod useless the day Bedrock ships one.
 */
@SpecImpl({
        "SC-100",
        "SC-100#manifest/format_version_1",
        "SC-100#manifest/format_version_2",
        "SC-100#manifest/format_version_3",
        "SC-100#manifest/module_resources",
        "SC-100#manifest/module_data",
        "SC-100#manifest/module_script",
        "SC-100#manifest/min_engine_version",
})
public final class ManifestParser {

    private ManifestParser() {
    }

    /** Parses, or returns empty having reported why. */
    public static Optional<Manifest> parse(JsonObject root, Provenance file, Diagnostics into) {
        int formatVersion = root.getNumber("format_version").map(JsonNumber::intValue).orElse(2);
        if (formatVersion < 1 || formatVersion > 3) {
            into.report(FormatDiagnostics.MANIFEST_UNKNOWN_FORMAT_VERSION.at(file, formatVersion));
            formatVersion = 2;
        }

        Optional<JsonObject> headerJson = root.getObject("header");
        if (headerJson.isEmpty()) {
            into.report(FormatDiagnostics.MANIFEST_UNUSABLE.at(file, "header"));
            return Optional.empty();
        }
        Provenance headerAt = file.at(JsonPointer.child(JsonPointer.ROOT, "header"));
        Optional<PackHeader> header = parseHeader(headerJson.get(), headerAt, into);
        if (header.isEmpty()) {
            return Optional.empty();
        }

        List<PackModule> modules = parseModules(root, file, into);
        if (modules.isEmpty()) {
            into.report(FormatDiagnostics.MANIFEST_UNUSABLE.at(file, "modules"));
            return Optional.empty();
        }

        Set<Capability> capabilities = new LinkedHashSet<>();
        Set<String> unknownCapabilities = new LinkedHashSet<>();
        root.getArray("capabilities").ifPresent(array -> {
            for (JsonValue value : array.values()) {
                value.asString().ifPresent(name -> Capability.parse(name)
                        .ifPresentOrElse(capabilities::add, () -> unknownCapabilities.add(name)));
            }
        });

        return Optional.of(new Manifest(
                formatVersion,
                header.get(),
                modules,
                parseDependencies(root, file, into),
                capabilities,
                unknownCapabilities,
                parseMetadata(root)));
    }

    private static Optional<PackHeader> parseHeader(
            JsonObject header, Provenance at, Diagnostics into) {
        Optional<String> rawUuid = header.getString("uuid");
        if (rawUuid.isEmpty()) {
            into.report(FormatDiagnostics.MANIFEST_UNUSABLE.at(at, "header.uuid"));
            return Optional.empty();
        }
        PackId id = PackId.parse(rawUuid.get()).orElseGet(() -> {
            // Real packs ship malformed UUIDs often enough that rejecting them would reject useful
            // content. The replacement is derived rather than random so the pack keeps one identity
            // across reloads, which is what the block ledger needs.
            into.report(FormatDiagnostics.MANIFEST_UUID_MALFORMED.at(at, rawUuid.get()));
            return PackId.derived(rawUuid.get());
        });

        SemanticVersion version = header.get("version")
                .map(value -> semanticVersion(value, at, "header.version", into))
                .orElseGet(() -> {
                    into.report(FormatDiagnostics.MANIFEST_VERSION_MALFORMED.at(at, "header.version"));
                    return SemanticVersion.ZERO;
                });

        BedrockVersion minEngine = header.get("min_engine_version")
                .flatMap(value -> bedrockVersion(value, at, "header.min_engine_version", into))
                .orElseGet(() -> {
                    into.report(FormatDiagnostics.MANIFEST_NO_MIN_ENGINE_VERSION.at(at));
                    return PackHeader.ASSUMED_MIN_ENGINE_VERSION;
                });

        BedrockVersion baseGame = header.get("base_game_version")
                .flatMap(value -> bedrockVersion(value, at, "header.base_game_version", into))
                .orElse(BedrockVersion.ZERO);

        return Optional.of(new PackHeader(
                id,
                header.getString("name").orElse(""),
                header.getString("description").orElse(""),
                version,
                minEngine,
                baseGame,
                header.getString("pack_scope").map(PackScope::parse).orElse(PackScope.ANY),
                parseSubpacks(header)));
    }

    private static List<SubpackDecl> parseSubpacks(JsonObject header) {
        List<SubpackDecl> out = new ArrayList<>();
        header.getArray("subpacks").ifPresent(array -> {
            for (JsonValue value : array.values()) {
                value.asObject().ifPresent(entry -> entry.getString("folder_name")
                        .filter(folder -> !folder.isBlank())
                        .ifPresent(folder -> out.add(new SubpackDecl(
                                folder,
                                entry.getString("name").orElse(folder),
                                entry.getNumber("memory_tier").map(JsonNumber::intValue).orElse(0)))));
            }
        });
        return out;
    }

    private static List<PackModule> parseModules(
            JsonObject root, Provenance file, Diagnostics into) {
        Optional<JsonArray> array = root.getArray("modules");
        if (array.isEmpty()) {
            return List.of();
        }
        List<PackModule> out = new ArrayList<>();
        String base = JsonPointer.child(JsonPointer.ROOT, "modules");
        for (int i = 0; i < array.get().size(); i++) {
            Optional<JsonObject> entry = array.get().values().get(i).asObject();
            if (entry.isEmpty()) {
                continue;
            }
            JsonObject module = entry.get();
            Provenance at = file.at(JsonPointer.index(base, i));

            String declaredType = module.getString("type").orElse("");
            ModuleType type = ModuleType.parse(declaredType);
            if (type == ModuleType.CLIENT_DATA) {
                into.report(FormatDiagnostics.MANIFEST_CLIENT_DATA_MODULE.at(at));
            } else if (type == ModuleType.UNKNOWN) {
                // Reported rather than dropped in silence. A module type nobody recognises is how a
                // pack contributes content that never appears, with nothing anywhere saying so.
                into.report(FormatDiagnostics.MANIFEST_UNKNOWN_MODULE_TYPE.at(at, declaredType));
            }

            String entryPoint = "";
            if (type == ModuleType.SCRIPT) {
                entryPoint = module.getString("entry").filter(s -> !s.isBlank()).orElse("");
                if (entryPoint.isEmpty()) {
                    // Absent from Mojang's published field table, universal in practice.
                    into.report(FormatDiagnostics.MANIFEST_SCRIPT_ENTRY_DEFAULTED.at(at));
                    entryPoint = PackModule.DEFAULT_SCRIPT_ENTRY;
                }
            }

            out.add(new PackModule(
                    type,
                    declaredType,
                    module.getString("uuid").flatMap(PackId::parse).orElse(PackId.NONE),
                    module.get("version")
                            .map(v -> semanticVersion(v, at, "modules.version", into))
                            .orElse(SemanticVersion.ZERO),
                    module.getString("description").orElse(""),
                    module.getString("language").orElse(""),
                    entryPoint));
        }
        return out;
    }

    private static List<PackDependency> parseDependencies(
            JsonObject root, Provenance file, Diagnostics into) {
        Optional<JsonArray> array = root.getArray("dependencies");
        if (array.isEmpty()) {
            return List.of();
        }
        List<PackDependency> out = new ArrayList<>();
        String base = JsonPointer.child(JsonPointer.ROOT, "dependencies");
        for (int i = 0; i < array.get().size(); i++) {
            Optional<JsonObject> entry = array.get().values().get(i).asObject();
            if (entry.isEmpty()) {
                continue;
            }
            JsonObject dependency = entry.get();
            Provenance at = file.at(JsonPointer.index(base, i));
            SemanticVersion version = dependency.get("version")
                    .map(v -> semanticVersion(v, at, "dependencies.version", into))
                    .orElse(SemanticVersion.ZERO);

            Optional<String> uuid = dependency.getString("uuid");
            Optional<String> moduleName = dependency.getString("module_name");
            if (uuid.isPresent()) {
                out.add(new PackDependency.OnPack(
                        PackId.parse(uuid.get()).orElseGet(() -> PackId.derived(uuid.get())),
                        version));
            } else if (moduleName.isPresent()) {
                out.add(new PackDependency.OnModule(moduleName.get(), version));
            } else {
                into.report(FormatDiagnostics.MANIFEST_DEPENDENCY_UNUSABLE.at(at));
            }
        }
        return out;
    }

    private static PackMetadata parseMetadata(JsonObject root) {
        Optional<JsonObject> metadata = root.getObject("metadata");
        if (metadata.isEmpty()) {
            return PackMetadata.EMPTY;
        }
        JsonObject m = metadata.get();
        return new PackMetadata(
                strings(m, "authors"),
                m.getString("license").orElse(""),
                m.getString("url").orElse(""),
                m.getString("product_type").orElse(""),
                strings(m, "generated_with"));
    }

    /**
     * Reads a string list, tolerating the two shapes real manifests use.
     *
     * <p>{@code generated_with} is documented as an object of tool to version list and is written as
     * a plain array at least as often. Both flatten to the same thing here, because nothing consumes
     * the structure — it is diagnostic text.
     */
    private static List<String> strings(JsonObject object, String key) {
        Optional<JsonValue> value = object.get(key);
        if (value.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        value.get().asArray().ifPresent(array ->
                array.values().forEach(v -> v.asString().ifPresent(out::add)));
        value.get().asObject().ifPresent(nested -> nested.members().forEach((name, versions) -> {
            List<String> each = new ArrayList<>();
            versions.asArray().ifPresent(a ->
                    a.values().forEach(v -> v.asString().ifPresent(each::add)));
            out.add(each.isEmpty() ? name : name + " " + String.join(", ", each));
        }));
        return out;
    }

    /**
     * Normalises either version shape. SC-100 §4.3.
     *
     * <p>Accepts the array form and the string form whatever the declared {@code format_version}
     * says, because the two disagree in real manifests often enough that trusting the declaration
     * would reject working packs.
     */
    private static SemanticVersion semanticVersion(
            JsonValue value, Provenance at, String field, Diagnostics into) {
        Optional<JsonArray> array = value.asArray();
        if (array.isPresent()) {
            List<Integer> parts = array.get().values().stream()
                    .map(v -> v.asNumber().map(JsonNumber::intValue).orElse(0))
                    .toList();
            if (parts.size() != 3) {
                into.report(FormatDiagnostics.MANIFEST_VERSION_MALFORMED.at(at, field, parts.size()));
            }
            return SemanticVersion.fromArray(parts);
        }
        Optional<String> text = value.asString();
        if (text.isPresent()) {
            Optional<SemanticVersion> parsed = SemanticVersion.tryParse(text.get());
            if (parsed.isPresent()) {
                return parsed.get();
            }
            // A version string that is not SemVer is usually a Bedrock-style "1.21" or "1.21.0.3".
            Optional<BedrockVersion> loose = BedrockVersion.tryParse(text.get());
            if (loose.isPresent()) {
                return SemanticVersion.of(
                        loose.get().major(), loose.get().minor(), loose.get().patch());
            }
        }
        into.report(FormatDiagnostics.MANIFEST_VERSION_MALFORMED.at(at, field, value.typeName()));
        return SemanticVersion.ZERO;
    }

    /** As {@link #semanticVersion}, for the engine-version fields, which are not SemVer. */
    private static Optional<BedrockVersion> bedrockVersion(
            JsonValue value, Provenance at, String field, Diagnostics into) {
        Optional<JsonArray> array = value.asArray();
        if (array.isPresent()) {
            return Optional.of(BedrockVersion.fromArray(array.get().values().stream()
                    .map(v -> v.asNumber().map(JsonNumber::intValue).orElse(0))
                    .toList()));
        }
        Optional<BedrockVersion> parsed = value.asString().flatMap(BedrockVersion::tryParse);
        if (parsed.isEmpty()) {
            into.report(FormatDiagnostics.MANIFEST_VERSION_MALFORMED.at(at, field, value.typeName()));
        }
        return parsed;
    }
}
