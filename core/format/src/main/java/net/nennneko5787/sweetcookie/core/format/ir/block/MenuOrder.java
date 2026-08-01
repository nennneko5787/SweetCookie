package net.nennneko5787.sweetcookie.core.format.ir.block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.ir.BehaviorIr;
import net.nennneko5787.sweetcookie.core.format.value.BedrockId;

/**
 * The order add-on content appears in the creative menu. SC-280 §5, SC-170.
 *
 * <p><b>One tab, grouped by pack.</b> A tab per add-on is not possible and the reason is the same
 * one the whole design rests on: {@code CreativeModeTab} is a registry entry, registries freeze
 * before any world loads, and packs are enabled per world afterwards. Registering one per pack would
 * also be registering something named after a Bedrock feature, which constitution rule 7 forbids —
 * the same rule that made blocks anonymous slots and items a single carrier.
 *
 * <p>The tab is fixed; its <b>contents</b> are not. So the effect a per-pack tab would have is
 * produced here instead: each pack's blocks sit together, in activation order, and disabling a pack
 * removes its run in one piece — which is what the user did, shown back to them.
 *
 * <p>Within a pack, Bedrock's own {@code menu_category} decides. It is already parsed, it is the
 * order the author saw in Bedrock's creative menu, and using anything else would be inventing a
 * taxonomy next to one that exists.
 */
@SpecImpl({"SC-280", "SC-170"})
public final class MenuOrder {

    /**
     * Bedrock's categories, in the order its own creative menu shows them.
     *
     * <p>An unrecognised or absent category sorts last rather than first. A block whose author left
     * the field off should not lead a pack's run ahead of the blocks that asked to be there.
     */
    private static final List<String> CATEGORIES =
            List.of("construction", "nature", "equipment", "items");

    private MenuOrder() {
    }

    /**
     * Every enabled pack's blocks, in the order the creative tab should list them.
     *
     * <p>Duplicates are dropped at the position they FIRST appear. Two packs may define the same
     * Bedrock identifier and the later one wins the definition (SC-100 §5), but it does not move the
     * entry: the block a player already knows stays where they last saw it.
     *
     * @param inPrecedenceOrder the enabled packs' behaviour halves, lowest priority first
     */
    public static List<BedrockId> of(List<BehaviorIr> inPrecedenceOrder) {
        Set<BedrockId> seen = new LinkedHashSet<>();
        for (BehaviorIr behavior : inPrecedenceOrder) {
            List<BlockDefIr> blocks = new ArrayList<>(behavior.blocks().values());
            blocks.sort(Comparator
                    .comparingInt((BlockDefIr block) -> rankOf(block.menuCategory()))
                    // Then by identifier, so that two blocks in the same category do not swap
                    // places between runs. Map iteration order is stable but pack order is not a
                    // promise anyone made.
                    .thenComparing(block -> block.identifier().toString()));
            blocks.forEach(block -> seen.add(block.identifier()));
        }
        return List.copyOf(seen);
    }

    private static int rankOf(String category) {
        int rank = CATEGORIES.indexOf(category == null
                ? ""
                : category.trim().toLowerCase(Locale.ROOT));
        return rank < 0 ? CATEGORIES.size() : rank;
    }
}
