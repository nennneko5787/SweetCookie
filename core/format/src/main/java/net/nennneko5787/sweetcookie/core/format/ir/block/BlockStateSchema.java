package net.nennneko5787.sweetcookie.core.format.ir.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.value.BedrockId;

/**
 * The ordered states of one block, and the mixed-radix index that encodes them. SC-150 §2.3.
 *
 * <p>SC-120 §6.1 gives a Bedrock block <b>one</b> opaque index property rather than one Java
 * property per Bedrock state, so that packs can attach and detach at runtime. This is the arithmetic
 * that makes that work: state values are digits in a mixed-radix number, least-significant first in
 * declaration order.
 *
 * <p><b>Declaration order is normative and the order is part of the on-disk format.</b> The index
 * appears in chunk storage and in the block ledger, so reordering a pack's {@code description.states}
 * re-maps every block already placed in every world. That is why SC-120 makes a schema change a
 * detectable event with a re-mapping step rather than something that happens quietly.
 */
@SpecImpl("SC-150")
public record BlockStateSchema(List<BlockStateIr> states) {

    public static final BlockStateSchema EMPTY = new BlockStateSchema(List.of());

    public BlockStateSchema {
        states = List.copyOf(states);
    }

    /** The number of distinct index values: the product of every state's value count. */
    public int size() {
        int product = 1;
        for (BlockStateIr state : states) {
            product *= Math.max(1, state.size());
        }
        return product;
    }

    public boolean isEmpty() {
        return states.isEmpty();
    }

    public Optional<BlockStateIr> state(BedrockId name) {
        return states.stream().filter(s -> s.name().equals(name)).findFirst();
    }

    /**
     * Encodes one value per state into an index.
     *
     * <p>A state missing from {@code values}, or given a value it does not permit, contributes its
     * default. Refusing would mean a single typo in one permutation costing the whole block, and
     * Bedrock itself falls back to the default here.
     */
    public int encode(Map<BedrockId, String> values) {
        int index = 0;
        int radix = 1;
        for (BlockStateIr state : states) {
            String value = values.get(state.name());
            int digit = value == null ? 0 : state.indexOf(value).orElse(0);
            index += digit * radix;
            radix *= Math.max(1, state.size());
        }
        return index;
    }

    /** The inverse of {@link #encode}. An out-of-range index wraps rather than throwing. */
    public Map<BedrockId, String> decode(int index) {
        Map<BedrockId, String> out = new LinkedHashMap<>();
        int remaining = Math.floorMod(index, Math.max(1, size()));
        for (BlockStateIr state : states) {
            int radix = Math.max(1, state.size());
            out.put(state.name(), state.valueAt(remaining % radix));
            remaining /= radix;
        }
        return out;
    }

    /** Every index, in order. The permutation resolver walks this once at bind time. */
    public List<Map<BedrockId, String>> allCombinations() {
        List<Map<BedrockId, String>> out = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            out.add(decode(i));
        }
        return out;
    }

    /** The index every freshly placed block starts at: each state's first declared value. */
    public int defaultIndex() {
        return 0;
    }
}
