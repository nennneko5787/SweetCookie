package net.nennneko5787.lepus.runtime.registry;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.registry.StateSchema;

/**
 * Which state a block is placed in. SC-150 §2.2.
 *
 * <p>Bedrock's placement traits are states like any other — they sit in the same mixed-radix index
 * as the pack's own {@code description.states} — but their <b>values come from the act of placing</b>
 * rather than from the pack. A block with {@code minecraft:placement_direction} that is always
 * placed in state zero always faces the same way, which is what happens if nothing computes them.
 *
 * <p>Only the trait states are decided here. Everything the pack declared keeps its first value,
 * because nothing in a placement says anything about it.
 */
@SpecImpl("SC-150")
final class PlacementStates {

    /**
     * Whether a direction trait records the way the player looks, or the way the block should face.
     *
     * <p><b>Asserted, not verified against a Bedrock client.</b> Bedrock documents the trait as the
     * player's own facing; packs then build models whose front is the value's direction, so a block
     * placed while looking north should show its front to the south — towards the player, the way
     * every vanilla furnace does. That reasoning gives the opposite, and this is the one place to
     * flip if a real client disagrees.
     */
    private static final boolean FACES_THE_PLAYER = true;

    private PlacementStates() {
    }

    /**
     * The state index to place in.
     *
     * <p>Zero when the block has no trait states, which is most of them — and zero is what the code
     * did unconditionally before this existed.
     */
    static int indexFor(StateSchema schema, BlockPlaceContext context) {
        Map<String, String> values = new LinkedHashMap<>();
        for (StateSchema.Entry state : schema.states()) {
            values.put(state.name(), valueFor(state, context));
        }
        return schema.encode(values);
    }

    /**
     * One state's value: computed for a trait, and the pack's first value for anything else.
     *
     * <p>Falling back to the first declared value rather than guessing is deliberate. A pack's own
     * states mean whatever the pack says they mean, and there is nothing in "the player clicked
     * here" that could decide one.
     */
    private static String valueFor(StateSchema.Entry state, BlockPlaceContext context) {
        String fallback = state.values().isEmpty() ? "" : state.values().get(0);
        Direction horizontal = context.getHorizontalDirection();
        Direction looking = context.getNearestLookingDirection();
        return switch (state.name()) {
            case "minecraft:cardinal_direction" ->
                    name(FACES_THE_PLAYER ? horizontal.getOpposite() : horizontal);
            case "minecraft:facing_direction" ->
                    name(FACES_THE_PLAYER ? looking.getOpposite() : looking);
            // The face that was clicked, which is where the block was attached. Not a facing: a
            // torch on a wall's east side has block_face east however the player was standing.
            case "minecraft:block_face" -> name(context.getClickedFace());
            // Which half of the clicked face. Above the middle is the top half, as a slab decides.
            case "minecraft:vertical_half" ->
                    context.getClickLocation().y - context.getClickedPos().getY() > 0.5
                            ? "top" : "bottom";
            default -> fallback;
        };
    }

    private static String name(Direction direction) {
        return direction.getName().toLowerCase(Locale.ROOT);
    }
}
