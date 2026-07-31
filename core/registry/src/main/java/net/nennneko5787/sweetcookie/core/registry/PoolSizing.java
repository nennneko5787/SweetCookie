package net.nennneko5787.sweetcookie.core.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * Decides how big the pool must be before the registry freezes. SC-120 §6.2.
 *
 * <p>The whole decision has to be taken <b>at startup</b>, because Java freezes its registries long
 * before a world is selected. A world whose ledger needs more slots than were registered cannot be
 * accommodated later at any price, so the question "how many" must be answered from every world in
 * the instance, not from the one about to load.
 *
 * <p>Pure, and separate from the file discovery that feeds it: given a config and what each world
 * requires, this says what to register or why it refuses. That makes the awkward cases — a world
 * that outgrew the config, a pinned pool that will not grow — testable without a save directory.
 */
@SpecImpl("SC-120")
public final class PoolSizing {

    /**
     * Configuration. SC-120 §10.
     *
     * @param configured  {@code blockPool}
     * @param autoGrow    {@code blockPoolAutoGrow}; on by default
     */
    public record Config(SlotPool configured, boolean autoGrow) {

        public static final Config DEFAULT = new Config(SlotPool.DEFAULT, true);
    }

    /** What to do. */
    public sealed interface Result {

        /** Register this pool. */
        record Register(SlotPool pool, List<Growth> growth) implements Result {
            public Register {
                growth = List.copyOf(growth);
            }

            public boolean grew() {
                return !growth.isEmpty();
            }
        }

        /**
         * A world needs more than the pinned config allows. {@code SCE-4013}.
         *
         * <p>Refusing rather than silently enlarging is the point of {@code blockPoolAutoGrow:
         * false}: an operator who pinned the palette size wants to be told, not accommodated.
         */
        record Refuse(List<Growth> shortfall) implements Result {
            public Refuse {
                shortfall = List.copyOf(shortfall);
            }
        }
    }

    /**
     * One size class that needs more than the config gives it.
     *
     * <p>Carries the numbers rather than a message, because SC-120 §8.1 requires the operator to be
     * told the class, the shortfall and the exact config change — a generic failure here leaves
     * them with no way forward.
     */
    public record Growth(int sizeClass, int configured, int required) {

        public int shortfall() {
            return Math.max(0, required - configured);
        }

        /** The line an operator has to change, spelled out. */
        public String advice() {
            return "sweetcookie.blockPool." + sizeClass + " = " + required
                    + " (currently " + configured + ", " + shortfall() + " more needed)";
        }
    }

    private PoolSizing() {
    }

    /**
     * @param worldRequirements what each world's ledger says it uses, one entry per world
     */
    public static Result effective(Config config, List<SlotPool> worldRequirements) {
        Map<Integer, Integer> required = new TreeMap<>();
        for (SlotPool world : worldRequirements) {
            world.capacities().forEach((sizeClass, count) ->
                    required.merge(sizeClass, count, Math::max));
        }

        List<Growth> growth = new ArrayList<>();
        required.forEach((sizeClass, count) -> {
            int have = config.configured().capacity(sizeClass);
            if (count > have) {
                growth.add(new Growth(sizeClass, have, count));
            }
        });

        if (growth.isEmpty()) {
            return new Result.Register(config.configured(), List.of());
        }
        if (!config.autoGrow()) {
            return new Result.Refuse(growth);
        }
        return new Result.Register(
                config.configured().grownTo(new SlotPool(required)), growth);
    }
}
