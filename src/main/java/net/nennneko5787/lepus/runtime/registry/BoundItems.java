package net.nennneko5787.lepus.runtime.registry;

import java.util.List;
import java.util.Map;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * What the enabled packs' items currently are. SC-170, SC-120 §4.
 *
 * <p>{@link BoundBlocks}' counterpart, and much smaller for a reason worth stating: an item needs no
 * slot, no ledger entry and nothing remembered per world (SC-120 §5), because its identity travels
 * in the stack rather than in a registry. So there is nothing here to allocate, nothing to keep
 * stable across reloads, and nothing that a disabled pack could strand.
 */
@SpecImpl({"SC-170", "SC-120"})
public final class BoundItems {

    /**
     * One item, resolved.
     *
     * <p>No name field: the name is a translation key derived from the identifier and resolved by
     * the client, so there is nothing here to store and nothing to go stale in one language.
     *
     * @param logicalId  what a stack of it carries
     * @param modelPath  the generated item-model path, which is also its identifier
     * @param profile    its components, resolved at bind time (SC-170 §2)
     * @param files      the pack files it needs — its icon, when the packs had one
     */
    public record Bound(String logicalId, String modelPath,
            net.nennneko5787.lepus.core.format.ir.item.ItemProfile profile,
            Map<String, byte[]> files) {

        public Bound {
            files = Map.copyOf(files);
        }
    }

    private static List<Bound> items = List.of();

    private BoundItems() {
    }

    /** Replaces the whole snapshot, in creative-menu order. */
    public static void replace(List<Bound> bound) {
        items = List.copyOf(bound);
    }

    /** Every enabled item, in the order the creative tab lists them. */
    public static List<Bound> all() {
        return items;
    }

    public static void clear() {
        items = List.of();
    }
}
