package net.nennneko5787.sweetcookie.core.format.pack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostic;
import net.nennneko5787.sweetcookie.core.format.diag.DiagnosticLog;
import net.nennneko5787.sweetcookie.core.format.json.JsonArray;
import net.nennneko5787.sweetcookie.core.format.json.JsonBool;
import net.nennneko5787.sweetcookie.core.format.json.JsonNumber;
import net.nennneko5787.sweetcookie.core.format.json.JsonObject;
import net.nennneko5787.sweetcookie.core.format.json.JsonString;
import net.nennneko5787.sweetcookie.core.format.json.JsonValue;
import net.nennneko5787.sweetcookie.core.format.manifest.Capability;
import net.nennneko5787.sweetcookie.core.format.manifest.PackDependency;
import net.nennneko5787.sweetcookie.core.format.manifest.PackModule;
import net.nennneko5787.sweetcookie.core.format.manifest.SubpackDecl;

/**
 * Renders a load result as JSON, for conformance goldens. SC-110 §11 family 3.
 *
 * <p>A golden is only useful if a diff means "the behaviour changed". Two things here exist purely
 * to make that true:
 *
 * <ul>
 *   <li><b>Paths are rewritten and separator-normalised.</b> A {@link PackSource} holds an absolute
 *       path, which differs per machine, and Windows spells it with backslashes. A golden containing
 *       either would fail on every machine but the one that wrote it.
 *   <li><b>Nothing is sorted here.</b> Load order, module order and diagnostic order are all part of
 *       the contract (SC-100 §5, SC-240 §3), so a golden that sorted them would stop detecting the
 *       regressions most worth catching. Canonical JSON sorts object <em>keys</em>; arrays keep
 *       their meaning.
 * </ul>
 */
@SpecImpl("SC-100")
public final class LoadedAddonJson {

    private LoadedAddonJson() {
    }

    /**
     * @param rewritePath maps a source path to a stable one — usually "relative to the case
     *     directory". Applied before separator normalisation.
     */
    public static JsonObject of(LoadedAddon addon, UnaryOperator<String> rewritePath) {
        Map<String, JsonValue> root = new LinkedHashMap<>();
        root.put("packs", array(addon.packs().stream()
                .map(pack -> (JsonValue) pack(pack, rewritePath)).toList()));
        root.put("diagnostics", diagnostics(addon.diagnostics(), rewritePath));
        return new JsonObject(root);
    }

    /** The diagnostics alone, which most cases assert on separately from the parse result. */
    public static JsonArray diagnostics(DiagnosticLog log, UnaryOperator<String> rewritePath) {
        List<JsonValue> out = new ArrayList<>();
        for (DiagnosticLog.Occurrence occurrence : log.occurrences()) {
            Diagnostic diagnostic = occurrence.diagnostic();
            Map<String, JsonValue> node = new LinkedHashMap<>();
            node.put("code", string(diagnostic.codeString()));
            node.put("severity", string(diagnostic.severity().name()));
            node.put("messageKey", string(diagnostic.messageKey()));
            node.put("count", JsonNumber.of(occurrence.count()));
            diagnostic.where().ifPresent(where -> {
                // Omitted rather than rendered as the zero UUID when the diagnostic fired before the
                // pack had an identity. Absence says that; a row of zeroes only looks like a bug.
                if (!where.pack().isNone()) {
                    node.put("pack", string(where.pack().toString()));
                }
                node.put("path", string(normalise(rewritePath.apply(where.path()))));
                if (!where.jsonPointer().isEmpty()) {
                    node.put("at", string(where.jsonPointer()));
                }
            });
            diagnostic.featureId().ifPresent(id -> node.put("feature", string(id)));
            // Arguments are deliberately omitted: they carry file names, byte counts and parser
            // positions, none of which are stable across machines, and a golden that churned on
            // them would be retired within a week.
            out.add(new JsonObject(node));
        }
        return array(out);
    }

    private static JsonObject pack(LoadedPack pack, UnaryOperator<String> rewritePath) {
        Map<String, JsonValue> node = new LinkedHashMap<>();
        node.put("id", string(pack.id().toString()));
        node.put("loadOrder", JsonNumber.of(pack.loadOrder()));
        node.put("source", string(normalise(rewritePath.apply(pack.source().toString()))));
        node.put("sourceKind", string(pack.source().kind().name()));
        node.put("formatVersion", JsonNumber.of(pack.manifest().formatVersion()));
        node.put("header", header(pack));
        node.put("modules", array(pack.modules().stream().map(m -> (JsonValue) module(m)).toList()));
        node.put("dependencies", array(pack.dependencies().stream()
                .map(d -> (JsonValue) dependency(d)).toList()));
        node.put("capabilities", array(pack.capabilities().stream()
                .map(Capability::declared).sorted().map(LoadedAddonJson::string).toList()));
        node.put("unknownCapabilities", array(pack.manifest().unknownCapabilities().stream()
                .sorted().map(LoadedAddonJson::string).toList()));
        node.put("subpack", subpack(pack));
        node.put("texts", texts(pack));
        // The resolved file list proves the subpack overlay did what it claimed, which no field of
        // the manifest can show.
        node.put("files", array(pack.vfs().paths().map(LoadedAddonJson::string).toList()));
        return new JsonObject(node);
    }

    private static JsonObject header(LoadedPack pack) {
        Map<String, JsonValue> node = new LinkedHashMap<>();
        node.put("name", string(pack.header().name()));
        node.put("description", string(pack.header().description()));
        node.put("version", string(pack.version().toString()));
        node.put("minEngineVersion", string(pack.header().minEngineVersion().toString()));
        node.put("baseGameVersion", string(pack.header().baseGameVersion().toString()));
        node.put("scope", string(pack.header().scope().name()));
        return new JsonObject(node);
    }

    private static JsonObject module(PackModule module) {
        Map<String, JsonValue> node = new LinkedHashMap<>();
        node.put("type", string(module.type().name()));
        node.put("declaredType", string(module.declaredType()));
        node.put("version", string(module.version().toString()));
        if (!module.language().isEmpty()) {
            node.put("language", string(module.language()));
        }
        if (!module.entry().isEmpty()) {
            node.put("entry", string(module.entry()));
        }
        return new JsonObject(node);
    }

    private static JsonObject dependency(PackDependency dependency) {
        Map<String, JsonValue> node = new LinkedHashMap<>();
        switch (dependency) {
            case PackDependency.OnPack onPack -> {
                node.put("kind", string("pack"));
                node.put("uuid", string(onPack.uuid().toString()));
            }
            case PackDependency.OnModule onModule -> {
                node.put("kind", string("module"));
                node.put("moduleName", string(onModule.moduleName()));
            }
        }
        node.put("version", string(dependency.version().toString()));
        return new JsonObject(node);
    }

    private static JsonObject subpack(LoadedPack pack) {
        Map<String, JsonValue> node = new LinkedHashMap<>();
        node.put("ceiling", JsonNumber.of(pack.subpacks().ceiling()));
        node.put("selected", pack.subpacks().selected()
                .map(SubpackDecl::folderName).map(LoadedAddonJson::string)
                .map(JsonValue.class::cast)
                .orElse(JsonBool.FALSE));
        node.put("available", array(pack.subpacks().available().stream()
                .map(s -> (JsonValue) new JsonObject(Map.of(
                        "folderName", string(s.folderName()),
                        "name", string(s.name()),
                        "memoryTier", JsonNumber.of(s.memoryTier()))))
                .toList()));
        return new JsonObject(node);
    }

    private static JsonObject texts(LoadedPack pack) {
        Map<String, JsonValue> node = new LinkedHashMap<>();
        node.put("languages", array(pack.texts().languages().stream()
                .map(LoadedAddonJson::string).toList()));
        Map<String, JsonValue> entries = new LinkedHashMap<>();
        pack.texts().byLocale().forEach((locale, values) -> {
            Map<String, JsonValue> perLocale = new LinkedHashMap<>();
            values.forEach((key, value) -> perLocale.put(key, string(value)));
            entries.put(locale, new JsonObject(perLocale));
        });
        node.put("entries", new JsonObject(entries));
        return new JsonObject(node);
    }

    /** Separators, so a golden written on Windows is readable on Linux and vice versa. */
    private static String normalise(String path) {
        return path.replace('\\', '/');
    }

    private static JsonValue string(String value) {
        return new JsonString(value);
    }

    private static JsonArray array(List<? extends JsonValue> values) {
        return new JsonArray(List.copyOf(values));
    }
}
