package net.nennneko5787.lepus.core.format.ir.block;

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
import net.nennneko5787.lepus.core.format.json.JsonNumber;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import net.nennneko5787.lepus.core.format.value.BedrockVersion;
import net.nennneko5787.lepus.core.format.value.Provenance;
import net.nennneko5787.lepus.core.molang.MolangExpr;
import net.nennneko5787.lepus.core.molang.MolangSyntaxException;

/** Reads a {@code blocks/*.json} file into {@link BlockDefIr}. SC-150, SC-110 §3. */
@SpecImpl("SC-150")
public final class BlockFiles {

    private static final String ROOT = "minecraft:block";

    private static final Set<String> ROOT_KEYS = Set.of("format_version", ROOT);
    private static final Set<String> BLOCK_KEYS =
            Set.of("description", "components", "permutations", "events");
    private static final Set<String> DESCRIPTION_KEYS = Set.of(
            "identifier", "states", "traits", "menu_category", "is_experimental",
            "properties", "register_to_creative_menu");

    private static final ParserRegistry<List<BlockDefIr>> REGISTRY =
            new ParserRegistry<List<BlockDefIr>>("block")
                    .register(BedrockVersion.of(1, 16, 0), BlockFiles::parseBlock);

    private BlockFiles() {
    }

    public static List<BlockDefIr> parse(JsonObject root, Provenance file, Diagnostics into) {
        return REGISTRY.parse(root, "format_version", file, into).orElse(List.of());
    }

    private static Optional<List<BlockDefIr>> parseBlock(JsonObject root, ParseContext ctx) {
        Optional<JsonObject> block = root.getObject(ROOT);
        if (block.isEmpty()) {
            ctx.at(ROOT).reportMissing(ROOT);
            return Optional.of(List.of());
        }
        JsonObject body = block.get();
        ParseContext at = ctx.at(ROOT);

        Optional<JsonObject> description = body.getObject("description");
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

        List<BlockTraitIr> traits = parseTraits(desc, descAt);
        BlockStateSchema schema = buildSchema(desc, traits, descAt);

        return Optional.of(List.of(new BlockDefIr(
                BedrockId.parse(identifier),
                schema,
                traits,
                componentsOf(body.getObject("components").orElse(JsonObject.EMPTY)),
                parsePermutations(body, at),
                desc.getObject("menu_category").flatMap(m -> m.getString("category")).orElse(""),
                at.provenance(),
                UnknownData.of(body, BLOCK_KEYS))));
    }

    /**
     * Builds the ordered state list. SC-150 §2.1, §2.3.
     *
     * <p>Declared states first, in the order the pack wrote them, then trait states in a fixed
     * order. Appending traits rather than interleaving them means enabling one cannot shift the
     * digits of the states already encoded in placed blocks.
     */
    private static BlockStateSchema buildSchema(
            JsonObject description, List<BlockTraitIr> traits, ParseContext ctx) {
        List<BlockStateIr> states = new ArrayList<>();
        Optional<JsonObject> declared = description.getObject("states");
        if (declared.isPresent()) {
            ParseContext at = ctx.at("states");
            for (String name : declared.get().keys()) {
                parseState(name, declared.get().members().get(name), at.at(name))
                        .ifPresent(states::add);
            }
        }
        for (BlockTraitIr trait : traits) {
            for (String stateName : trait.enabled()) {
                List<String> values = BlockTraitIr.valuesOf(stateName);
                if (values.isEmpty()) {
                    ctx.at("traits").report(IrDiagnostics.FIELD_MALFORMED, stateName, "unknown trait state");
                    continue;
                }
                states.add(new BlockStateIr(
                        BedrockId.parse(stateName), values, BlockStateIr.Kind.STRING));
            }
        }
        return new BlockStateSchema(states);
    }

    private static Optional<BlockStateIr> parseState(
            String name, JsonValue value, ParseContext ctx) {
        List<String> values = new ArrayList<>();
        BlockStateIr.Kind kind = BlockStateIr.Kind.STRING;

        Optional<JsonArray> list = value.asArray();
        if (list.isPresent()) {
            kind = kindOf(list.get());
            for (JsonValue element : list.get().values()) {
                values.add(scalar(element));
            }
        } else {
            // {"values": {"min": 0, "max": 15}} - the range form, integers only.
            Optional<JsonObject> range = value.asObject().flatMap(o -> o.getObject("values"));
            if (range.isEmpty()) {
                ctx.report(IrDiagnostics.FIELD_MALFORMED, name, value.typeName());
                return Optional.empty();
            }
            int min = range.get().getNumber("min").map(JsonNumber::intValue).orElse(0);
            int max = range.get().getNumber("max").map(JsonNumber::intValue).orElse(0);
            if (max < min) {
                ctx.report(IrDiagnostics.FIELD_MALFORMED, name, "max below min");
                return Optional.empty();
            }
            for (int i = min; i <= max; i++) {
                values.add(Integer.toString(i));
            }
            kind = BlockStateIr.Kind.INTEGER;
        }

        if (values.isEmpty()) {
            ctx.report(IrDiagnostics.FIELD_MALFORMED, name, "no values");
            return Optional.empty();
        }
        if (values.size() > BlockStateIr.MAX_VALUES) {
            // Bedrock's own cap. Truncating keeps the block usable and keeps the index stable for
            // the values that fit, where refusing would lose the whole block.
            ctx.report(IrDiagnostics.FIELD_MALFORMED, name,
                    values.size() + " values; the cap is " + BlockStateIr.MAX_VALUES);
            values = values.subList(0, BlockStateIr.MAX_VALUES);
        }
        return Optional.of(new BlockStateIr(BedrockId.parse(name), values, kind));
    }

    private static BlockStateIr.Kind kindOf(JsonArray values) {
        if (values.isEmpty()) {
            return BlockStateIr.Kind.STRING;
        }
        JsonValue first = values.values().get(0);
        if (first.asBool().isPresent()) {
            return BlockStateIr.Kind.BOOLEAN;
        }
        return first.asNumber().isPresent() ? BlockStateIr.Kind.INTEGER : BlockStateIr.Kind.STRING;
    }

    private static String scalar(JsonValue value) {
        return value.asString()
                .or(() -> value.asBool().map(String::valueOf))
                .or(() -> value.asNumber().map(n -> n.isIntegral()
                        ? Integer.toString(n.intValue())
                        : Float.toString(n.floatValue())))
                .orElse("");
    }

    private static List<BlockTraitIr> parseTraits(JsonObject description, ParseContext ctx) {
        Optional<JsonObject> traits = description.getObject("traits");
        if (traits.isEmpty()) {
            return List.of();
        }
        List<BlockTraitIr> out = new ArrayList<>();
        ParseContext at = ctx.at("traits");
        for (String name : traits.get().keys()) {
            BedrockId id = BedrockId.parse(name);
            Optional<BlockTraitIr.Known> known = BlockTraitIr.Known.of(id);
            if (known.isEmpty()) {
                at.at(name).report(IrDiagnostics.FIELD_MALFORMED, name, "unknown trait");
                continue;
            }
            List<String> enabled = new ArrayList<>();
            Optional<JsonObject> body = traits.get().getObject(name);
            for (String stateName : known.get().states()) {
                String key = stateName.substring(stateName.indexOf(':') + 1);
                boolean on = body.map(b -> b.getBool("enabled_states")
                                .orElseGet(() -> b.getArray("enabled_states")
                                        .map(a -> a.values().stream()
                                                .flatMap(v -> v.asString().stream())
                                                .anyMatch(s -> s.endsWith(key)))
                                        .orElse(false)))
                        .orElse(false);
                if (on) {
                    enabled.add(stateName);
                }
            }
            out.add(new BlockTraitIr(id, enabled));
        }
        return out;
    }

    private static List<BlockPermutationIr> parsePermutations(JsonObject body, ParseContext ctx) {
        Optional<JsonArray> permutations = body.getArray("permutations");
        if (permutations.isEmpty()) {
            return List.of();
        }
        List<BlockPermutationIr> out = new ArrayList<>();
        ParseContext arrayAt = ctx.at("permutations");
        for (int i = 0; i < permutations.get().size(); i++) {
            ParseContext at = arrayAt.at(i);
            Optional<JsonObject> entry = permutations.get().values().get(i).asObject();
            if (entry.isEmpty()) {
                at.report(IrDiagnostics.FIELD_MALFORMED, "permutations", "not an object");
                continue;
            }
            String source = entry.get().getString("condition").orElse("");
            if (source.isBlank()) {
                at.at("condition").reportMissing("condition");
                continue;
            }
            MolangExpr condition;
            try {
                condition = MolangExpr.compile(source);
            } catch (MolangSyntaxException e) {
                // Dropped, not defaulted. A condition that always matches and one that never
                // matches are both wrong, and each silently changes what the block looks like in
                // half its states.
                at.at("condition").report(IrDiagnostics.FIELD_MALFORMED, "condition", e.getMessage());
                continue;
            }
            if (!condition.unresolved().isEmpty()) {
                at.at("condition").report(IrDiagnostics.FIELD_MALFORMED, "condition",
                        "unknown names " + condition.unresolved());
            }
            out.add(new BlockPermutationIr(
                    condition,
                    componentsOf(entry.get().getObject("components").orElse(JsonObject.EMPTY))));
        }
        return out;
    }

    private static Map<BedrockId, JsonValue> componentsOf(JsonObject components) {
        Map<BedrockId, JsonValue> out = new LinkedHashMap<>();
        components.members().forEach((name, value) -> out.put(BedrockId.parse(name), value));
        return out;
    }
}
