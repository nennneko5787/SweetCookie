package net.nennneko5787.lepus.core.format.ir.block;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.value.BedrockId;

/**
 * An engine-provided state group. SC-150 §2.2.
 *
 * <p>A trait is a state the engine fills in rather than the pack: {@code placement_direction} gives
 * a block the direction its placer faced without the pack declaring or setting anything. They expand
 * into ordinary states before the index is built, and are <b>appended after</b> the pack's own
 * declared states in a fixed order (SC-120 §6.1) so that adding a trait cannot shift the digits of
 * the states already encoded in placed blocks.
 *
 * @param name    the trait identifier
 * @param enabled the state names this trait contributes, as the pack enabled them
 */
@SpecImpl("SC-150")
public record BlockTraitIr(BedrockId name, List<String> enabled) {

    /** The two traits Bedrock defines, with the states each can contribute. */
    public enum Known {
        PLACEMENT_DIRECTION("minecraft:placement_direction",
                List.of("minecraft:cardinal_direction", "minecraft:facing_direction")),
        PLACEMENT_POSITION("minecraft:placement_position",
                List.of("minecraft:block_face", "minecraft:vertical_half"));

        private final String id;
        private final List<String> states;

        Known(String id, List<String> states) {
            this.id = id;
            this.states = states;
        }

        public String id() {
            return id;
        }

        public List<String> states() {
            return states;
        }

        public static Optional<Known> of(BedrockId name) {
            String needle = name.toString().toLowerCase(Locale.ROOT);
            for (Known known : values()) {
                if (known.id.equals(needle)) {
                    return Optional.of(known);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * The values a trait state takes.
     *
     * <p>Fixed by the engine, and their order is as load-bearing as a declared state's: it is part
     * of the index that reaches chunk storage.
     */
    public static List<String> valuesOf(String stateName) {
        return switch (stateName.toLowerCase(Locale.ROOT)) {
            case "minecraft:cardinal_direction" -> List.of("south", "west", "north", "east");
            case "minecraft:facing_direction" ->
                    List.of("down", "up", "south", "west", "north", "east");
            case "minecraft:block_face" -> List.of("down", "up", "south", "west", "north", "east");
            case "minecraft:vertical_half" -> List.of("bottom", "top");
            case "minecraft:y_rotation_offset" -> List.of("0", "90", "180", "270");
            default -> List.of();
        };
    }

    public BlockTraitIr {
        enabled = List.copyOf(enabled);
    }
}
