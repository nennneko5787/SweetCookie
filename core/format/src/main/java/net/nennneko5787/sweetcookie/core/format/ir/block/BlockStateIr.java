package net.nennneko5787.sweetcookie.core.format.ir.block;

import java.util.List;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.value.BedrockId;

/**
 * One named block state and the values it can take. SC-150 §2.1.
 *
 * <p>Bedrock writes these two ways — an explicit list, or {@code {"values": {"min": 0, "max": 15}}}
 * — and both normalise here into a value list. The list is what the mixed-radix index encoding
 * counts against, so its <b>order is normative</b> (SC-150 §2.3): reordering it silently re-maps
 * every block already placed in every world.
 *
 * <p>Values are kept as strings whatever their JSON type. A state's values may be booleans, integers
 * or strings, they are only ever compared for equality, and a common type removes a three-way branch
 * from every site that touches one. {@link #kind()} keeps the original type for the places that care
 * — the Molang binding has to answer {@code query.block_state} with a number for an integer state
 * and an interned string for a string one.
 *
 * @param name   the state's identifier, namespaced by its pack
 * @param values permitted values in declaration order, at most 16
 * @param kind   the JSON type the pack wrote them as
 */
@SpecImpl("SC-150")
public record BlockStateIr(BedrockId name, List<String> values, Kind kind) {

    /** Bedrock caps a state at 16 values. SC-150 §2.1. */
    public static final int MAX_VALUES = 16;

    public enum Kind {
        BOOLEAN, INTEGER, STRING
    }

    public BlockStateIr {
        values = List.copyOf(values);
    }

    public int size() {
        return values.size();
    }

    /** The index of {@code value}, or empty when the state does not permit it. */
    public Optional<Integer> indexOf(String value) {
        int index = values.indexOf(value);
        return index < 0 ? Optional.empty() : Optional.of(index);
    }

    /** The value at {@code index}, clamped rather than thrown — an out-of-range index is data. */
    public String valueAt(int index) {
        if (values.isEmpty()) {
            return "";
        }
        return values.get(Math.floorMod(index, values.size()));
    }

    /** The default: Bedrock uses the first declared value. */
    public String defaultValue() {
        return values.isEmpty() ? "" : values.get(0);
    }
}
