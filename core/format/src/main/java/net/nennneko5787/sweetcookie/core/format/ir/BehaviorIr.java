package net.nennneko5787.sweetcookie.core.format.ir;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.ir.block.BlockDefIr;
import net.nennneko5787.sweetcookie.core.format.value.BedrockId;

/**
 * The behavior-pack half of one pack's IR. SC-110 §8.1.
 *
 * <p>One field so far, like {@link ResourceIr}. Entities, items, recipes, loot tables, trading,
 * spawn rules and functions each arrive with their domain document and their conformance cases; an
 * empty map is "not parsed yet" and not a claim that the pack has none.
 *
 * @param blocks keyed by {@code description.identifier}, in the order the pack's files were walked
 */
@SpecImpl("SC-110")
public record BehaviorIr(Map<BedrockId, BlockDefIr> blocks) {

    public static final BehaviorIr EMPTY = new BehaviorIr(Map.of());

    public BehaviorIr {
        blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
    }

    public Optional<BlockDefIr> block(BedrockId identifier) {
        return Optional.ofNullable(blocks.get(identifier));
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }
}
