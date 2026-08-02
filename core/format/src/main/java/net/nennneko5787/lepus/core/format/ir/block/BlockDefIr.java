package net.nennneko5787.lepus.core.format.ir.block;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.UnknownData;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import net.nennneko5787.lepus.core.format.value.Provenance;
import net.nennneko5787.lepus.core.molang.MolangExpr;

/**
 * One {@code minecraft:block} definition. SC-150.
 *
 * <p>Components are held as raw {@link JsonValue} keyed by identifier, not as typed records. That is
 * deliberate for this stage: the 54 component, trigger and event-response entries in the coverage
 * ledger each need their own translation and their own conformance case, and typing them one at a
 * time behind a map is a smaller change at every step than typing them all at once behind a record
 * with 54 optional fields. The map is what permutation resolution merges, and merging is defined on
 * keys.
 *
 * @param identifier   {@code description.identifier}
 * @param schema       the ordered states and their index encoding
 * @param traits       engine-provided states, expanded into {@code schema} before it is built
 * @param components   the base component set, keyed by component identifier
 * @param permutations in declaration order; later ones win (SC-150 §3)
 * @param menuCategory {@code description.menu_category.category}, empty when undeclared
 * @param provenance   pack, file, position, declared and effective version
 * @param unknown      keys this build does not recognise, kept verbatim (SC-110 §5)
 */
@SpecImpl("SC-150")
public record BlockDefIr(
        BedrockId identifier,
        BlockStateSchema schema,
        List<BlockTraitIr> traits,
        Map<BedrockId, JsonValue> components,
        List<BlockPermutationIr> permutations,
        String menuCategory,
        Provenance provenance,
        UnknownData unknown) {

    public BlockDefIr {
        traits = List.copyOf(traits);
        components = Collections.unmodifiableMap(new LinkedHashMap<>(components));
        permutations = List.copyOf(permutations);
    }

    public Optional<JsonValue> component(BedrockId id) {
        return Optional.ofNullable(components.get(id));
    }

    /**
     * The component set for one state index. SC-150 §3.
     *
     * <p>Pre-resolved at bind time rather than evaluated per block access: a permutation condition
     * may only read block state and pure maths, so every index has a fixed answer, and evaluating
     * Molang inside a shape lookup would be indefensible.
     *
     * <p>Merging is <b>per top-level component key</b>, later permutation wins. A permutation that
     * sets {@code minecraft:collision_box} replaces the base one entirely rather than merging into
     * it — the two are alternative shapes, not a shape and a patch.
     */
    public Map<BedrockId, JsonValue> resolve(int stateIndex) {
        Map<BedrockId, JsonValue> resolved = new LinkedHashMap<>(components);
        Map<BedrockId, String> values = schema.decode(stateIndex);
        BlockStateContext context = new BlockStateContext(schema, values);
        for (BlockPermutationIr permutation : permutations) {
            if (permutation.matches(context)) {
                resolved.putAll(permutation.components());
            }
        }
        return Collections.unmodifiableMap(resolved);
    }

    /** Every state index's resolved component set, in index order. */
    public List<Map<BedrockId, JsonValue>> resolveAll() {
        int size = schema.size();
        List<Map<BedrockId, JsonValue>> out = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(resolve(i));
        }
        return out;
    }

    /** True when no permutation condition can change anything, so one resolve serves every index. */
    public boolean isUniform() {
        return permutations.isEmpty() || schema.isEmpty();
    }

    /** Every expression this block compiles, for a caller reporting unresolved names. */
    public List<MolangExpr> expressions() {
        return permutations.stream().map(BlockPermutationIr::condition).toList();
    }
}
