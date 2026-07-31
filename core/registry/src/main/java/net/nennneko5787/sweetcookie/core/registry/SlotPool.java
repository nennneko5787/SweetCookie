package net.nennneko5787.sweetcookie.core.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * How many anonymous blocks are reserved in each size class. SC-120 §6.2.
 *
 * <p>Reserved slots cost palette space whether a pack uses them or not, so the default is a
 * deliberate trade rather than a maximum: 2,012 blocks and 56,832 block states, against vanilla's
 * roughly 1,100 and 27,000.
 *
 * <p>The effective pool at startup is the element-wise maximum of this default, what every world's
 * ledger in the instance requires, and what the installed packs require — so it grows across
 * restarts and never shrinks below what a saved world needs.
 *
 * @param capacities size class to reserved block count, ascending by size class
 */
@SpecImpl("SC-120")
public record SlotPool(Map<Integer, Integer> capacities) {

    /** SC-120 §6.2's table. */
    public static final SlotPool DEFAULT = of(
            1, 1024, 2, 256, 4, 256, 8, 128, 16, 128, 32, 64,
            64, 64, 128, 32, 256, 32, 512, 16, 1024, 8, 4096, 4);

    public SlotPool {
        Map<Integer, Integer> sorted = new TreeMap<>();
        capacities.forEach((sizeClass, count) -> {
            if (sizeClass < 1 || Integer.bitCount(sizeClass) != 1) {
                throw new IllegalArgumentException("size class must be a power of two: " + sizeClass);
            }
            if (count < 0) {
                throw new IllegalArgumentException("capacity must not be negative: " + count);
            }
            sorted.put(sizeClass, count);
        });
        capacities = Map.copyOf(sorted);
    }

    private static SlotPool of(int... pairs) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return new SlotPool(map);
    }

    public int capacity(int sizeClass) {
        return capacities.getOrDefault(sizeClass, 0);
    }

    /** The smallest reserved class with room for {@code stateCount} states. */
    public Optional<Integer> classFor(int stateCount) {
        int needed = Math.max(1, stateCount);
        return capacities.keySet().stream()
                .sorted()
                .filter(sizeClass -> sizeClass >= needed && capacity(sizeClass) > 0)
                .findFirst();
    }

    /** Total reserved blocks. What the registry pays for. */
    public int totalBlocks() {
        return capacities.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Total reserved block states. What the chunk palette pays for. */
    public int totalStates() {
        return capacities.entrySet().stream()
                .mapToInt(e -> e.getKey() * e.getValue())
                .sum();
    }

    /** True when every class of {@code required} fits inside this pool. */
    public boolean covers(SlotPool required) {
        return shortfallAgainst(required).isEmpty();
    }

    /**
     * Which classes {@code required} needs more of than this pool has, and by how much.
     *
     * <p>Returns the numbers rather than a boolean because SC-120 §8.1 requires an operator to be
     * told the class and the shortfall and the exact config line — a bare "does not fit" leaves them
     * with no way forward.
     *
     * @return size class to the count {@code required} needs, for classes that do not fit
     */
    public Map<Integer, Integer> shortfallAgainst(SlotPool required) {
        Map<Integer, Integer> short_ = new TreeMap<>();
        required.capacities.forEach((sizeClass, count) -> {
            if (capacity(sizeClass) < count) {
                short_.put(sizeClass, count);
            }
        });
        return Map.copyOf(short_);
    }
}
