package net.nennneko5787.lepus.core.format.ir.attachable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.ir.IrDiagnostics;
import net.nennneko5787.lepus.core.format.ir.ParseContext;
import net.nennneko5787.lepus.core.format.ir.ParserRegistry;
import net.nennneko5787.lepus.core.format.ir.UnknownData;
import net.nennneko5787.lepus.core.format.json.JsonArray;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import net.nennneko5787.lepus.core.format.value.BedrockVersion;
import net.nennneko5787.lepus.core.format.value.Provenance;

/**
 * Reads {@code attachables/*.json}. SC-170 §5, SC-110 §3.
 *
 * <p>The same shape as {@code ItemFiles} and {@code BlockFiles}: one root member, a
 * {@code description}, and a version registry that a future format lands in as a registration
 * rather than as a branch.
 *
 * <p><b>Nothing here resolves anything.</b> A geometry identifier, a texture path, an animation
 * name and a controller name are all just names at this point, and the files they name may be in
 * another pack, may be absent, or may not have been read yet. Resolving at parse time would make
 * the order files are walked in part of the answer.
 */
@SpecImpl("SC-170#attachable/item")
public final class AttachableFiles {

    private static final String ROOT = "minecraft:attachable";

    private static final Set<String> DESCRIPTION_KEYS = Set.of(
            "identifier", "min_engine_version", "materials", "textures", "geometry", "animations",
            "scripts", "render_controllers", "particle_effects", "sound_effects", "spawn_egg",
            "enable_attachables", "held_item_ignores_lighting", "hide_armor", "item",
            "queryable_geometry");

    private static final ParserRegistry<List<AttachableIr>> REGISTRY =
            new ParserRegistry<List<AttachableIr>>("attachable")
                    .register(BedrockVersion.of(1, 8, 0), AttachableFiles::parseAttachable);

    private AttachableFiles() {
    }

    /** Every attachable in one file. Empty when nothing could be read. */
    public static List<AttachableIr> parse(JsonObject root, Provenance file, Diagnostics into) {
        return REGISTRY.parse(root, "format_version", file, into).orElse(List.of());
    }

    private static Optional<List<AttachableIr>> parseAttachable(JsonObject root, ParseContext ctx) {
        Optional<JsonObject> attachable = root.getObject(ROOT);
        if (attachable.isEmpty()) {
            ctx.at(ROOT).reportMissing(ROOT);
            return Optional.of(List.of());
        }
        ParseContext at = ctx.at(ROOT);
        Optional<JsonObject> description = attachable.get().getObject("description");
        if (description.isEmpty()) {
            at.at("description").reportMissing("description");
            return Optional.of(List.of());
        }
        JsonObject desc = description.get();
        ParseContext descAt = at.at("description");

        String identifier = desc.getString("identifier").orElse("");
        if (identifier.isBlank()) {
            descAt.at("identifier").reportMissing("identifier");
            return Optional.of(List.of());
        }

        JsonObject scripts = desc.getObject("scripts").orElse(JsonObject.EMPTY);
        return Optional.of(List.of(new AttachableIr(
                BedrockId.parse(identifier),
                // `geometry`, singular, holding a map. Bedrock's own spelling; the plural would be
                // the obvious guess and finds nothing.
                strings(desc.getObject("geometry").orElse(JsonObject.EMPTY)),
                strings(desc.getObject("textures").orElse(JsonObject.EMPTY)),
                strings(desc.getObject("materials").orElse(JsonObject.EMPTY)),
                strings(desc.getObject("animations").orElse(JsonObject.EMPTY)),
                animate(scripts, descAt.at("scripts")),
                lines(scripts.get("pre_animation").orElse(null)),
                lines(desc.get("render_controllers").orElse(null)),
                descAt.provenance(),
                UnknownData.of(desc, DESCRIPTION_KEYS))));
    }

    /**
     * {@code scripts.animate}, whose entries have two shapes in one array.
     *
     * <p>A bare string plays unconditionally. A single-entry object plays that entry while its
     * Molang is true. Real packs mix both in one list, so an implementation that assumed either
     * shape alone would read half of a real file and report nothing about the other half.
     *
     * <p>An object with more than one member is not something Bedrock writes; each member is taken
     * as its own entry rather than the whole object being dropped, because losing an animation is
     * worse than reading a file nobody writes.
     */
    private static List<AttachableIr.Play> animate(JsonObject scripts, ParseContext ctx) {
        List<AttachableIr.Play> out = new ArrayList<>();
        Optional<JsonArray> animate = scripts.getArray("animate");
        if (animate.isEmpty()) {
            return out;
        }
        List<JsonValue> values = animate.get().values();
        for (int i = 0; i < values.size(); i++) {
            JsonValue entry = values.get(i);
            Optional<String> bare = entry.asString();
            if (bare.isPresent()) {
                out.add(new AttachableIr.Play(bare.get(), Optional.empty()));
                continue;
            }
            Optional<JsonObject> conditional = entry.asObject();
            if (conditional.isEmpty()) {
                ctx.at("animate").at(i).report(
                        IrDiagnostics.FIELD_MALFORMED, "animate", "not a name or a condition");
                continue;
            }
            conditional.get().members().forEach((name, condition) ->
                    out.add(new AttachableIr.Play(name, condition.asString())));
        }
        return out;
    }

    /** A JSON array of strings, or nothing. Used for both script bodies and controller lists. */
    private static List<String> lines(JsonValue value) {
        if (value == null) {
            return List.of();
        }
        return value.asArray()
                .map(array -> array.values().stream()
                        .map(JsonValue::asString)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toList())
                // A single string where an array goes is legal enough to read rather than refuse.
                .orElseGet(() -> value.asString().map(List::of).orElse(List.of()));
    }

    /** An object's string members, in file order. Anything that is not a string is skipped. */
    private static Map<String, String> strings(JsonObject object) {
        Map<String, String> out = new LinkedHashMap<>();
        object.members().forEach((key, value) -> value.asString().ifPresent(text ->
                out.put(key, text)));
        return out;
    }
}
