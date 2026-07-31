package net.nennneko5787.sweetcookie.core.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * One world's block slot assignments. SC-120 §6.3.
 *
 * <p><b>Allocated, persisted and never recomputed.</b> A logical identifier in the ledger keeps its
 * slot forever, even while its pack is absent — slots are never reused, never compacted and never
 * auto-freed. That is what makes disabling a pack non-destructive (constitution rule 5): the blocks
 * placed in the world still decode, and re-enabling restores them exactly.
 *
 * <p>Mutable, because it is a record of decisions taken over a world's lifetime. Every mutation
 * returns what it did so the caller can raise the right diagnostic.
 */
@SpecImpl("SC-120")
public final class BlockLedger {

    /** Bumped only when the on-disk shape changes. Reading an unknown version is a refusal. */
    public static final int FORMAT_VERSION = 1;

    /**
     * One bound block.
     *
     * @param logicalId       SC-120 §3's derived identifier
     * @param bedrockId       what it was derived from, for diagnostics and for a rebuild
     * @param slot            the physical slot; chunk palettes and this file only
     * @param schema          the state list as of {@code lastSeen}
     * @param previousSchemas older schemas, newest last, kept so stale chunks still decode
     */
    public record Binding(
            String logicalId,
            String bedrockId,
            BlockSlot slot,
            StateSchema schema,
            List<StateSchema> previousSchemas) {

        public Binding {
            previousSchemas = List.copyOf(previousSchemas);
        }

        public String schemaHash() {
            return schema.hash();
        }
    }

    /** What happened when a block was offered to the ledger. */
    public sealed interface Outcome {

        /** Already bound, and its schema is unchanged. */
        record Unchanged(Binding binding) implements Outcome {
        }

        /** Newly bound. */
        record Allocated(Binding binding) implements Outcome {
        }

        /**
         * Bound already, schema changed, and the new one still fits the bound class.
         *
         * <p>{@code SCE-4011}. Placed blocks are remapped lazily per chunk on load, never as a
         * world-wide sweep.
         */
        record Remapped(Binding binding, StateSchema previous) implements Outcome {
        }

        /**
         * Schema changed and no longer fits; a larger slot was taken.
         *
         * <p>{@code SCE-4012}. The old slot is retained as a placeholder that remaps on read, which
         * is why {@link #previousSlot} is reported rather than freed.
         */
        record Reallocated(Binding binding, BlockSlot previousSlot, StateSchema previous)
                implements Outcome {
        }

        /**
         * The size class it needs is full.
         *
         * <p>{@code SCE-4010}, and the one thing in SC-120 that still needs a restart. The
         * diagnostic must name the class, the shortfall and the config change — a generic failure
         * here leaves an operator with no way forward.
         */
        record Exhausted(String logicalId, int sizeClass, int needed) implements Outcome {
        }
    }

    private final SlotPool pool;
    private final Map<String, Binding> byLogicalId = new TreeMap<>();
    private final Map<Integer, java.util.BitSet> used = new LinkedHashMap<>();

    public BlockLedger(SlotPool pool) {
        this.pool = pool;
    }

    /** Rebuilds a ledger read from disk. Bindings are taken as authoritative, not re-derived. */
    public static BlockLedger restore(SlotPool pool, List<Binding> bindings) {
        BlockLedger ledger = new BlockLedger(pool);
        for (Binding binding : bindings) {
            ledger.byLogicalId.put(binding.logicalId(), binding);
            ledger.mark(binding.slot());
        }
        return ledger;
    }

    public SlotPool pool() {
        return pool;
    }

    public Optional<Binding> binding(String logicalId) {
        return Optional.ofNullable(byLogicalId.get(logicalId));
    }

    /** Every binding, in ascending logical-identifier order, which is how it is written out. */
    public List<Binding> bindings() {
        return List.copyOf(byLogicalId.values());
    }

    /**
     * Binds one block, or reports why it could not be.
     *
     * <p>Callers <b>must</b> iterate content in ascending {@code logicalId} order for allocation to
     * be deterministic (SC-120 §6.3 rule 2); {@link #bindAll} does that for them.
     */
    public Outcome bind(String logicalId, String bedrockId, StateSchema schema) {
        Binding existing = byLogicalId.get(logicalId);
        if (existing == null) {
            return allocate(logicalId, bedrockId, schema);
        }
        if (existing.schemaHash().equals(schema.hash())) {
            return new Outcome.Unchanged(existing);
        }
        if (schema.size() <= existing.slot().sizeClass()) {
            // The slot is kept and placed blocks are remapped. Keeping the slot is what stops a
            // pack update from orphaning every block it placed.
            List<StateSchema> history = new ArrayList<>(existing.previousSchemas());
            history.add(existing.schema());
            Binding updated =
                    new Binding(logicalId, bedrockId, existing.slot(), schema, history);
            byLogicalId.put(logicalId, updated);
            return new Outcome.Remapped(updated, existing.schema());
        }
        Outcome larger = allocate(logicalId, bedrockId, schema);
        if (larger instanceof Outcome.Allocated allocated) {
            List<StateSchema> history = new ArrayList<>(existing.previousSchemas());
            history.add(existing.schema());
            Binding updated = new Binding(
                    logicalId, bedrockId, allocated.binding().slot(), schema, history);
            byLogicalId.put(logicalId, updated);
            // The old slot is NOT released: it still holds placed blocks that decode with the old
            // schema, and freeing it would hand those blocks to unrelated content.
            return new Outcome.Reallocated(updated, existing.slot(), existing.schema());
        }
        return larger;
    }

    /**
     * Binds a whole content set deterministically.
     *
     * <p>Ascending logical-identifier order, so the same packs against a clean world produce the
     * same ledger on every machine and every run — which is SC-120 §12's determinism test and the
     * property that makes a ledger diff reviewable.
     *
     * @param content logical identifier to (Bedrock identifier, schema)
     */
    public List<Outcome> bindAll(Map<String, Map.Entry<String, StateSchema>> content) {
        List<Outcome> out = new ArrayList<>();
        List<String> ordered = new ArrayList<>(content.keySet());
        ordered.sort(Comparator.naturalOrder());
        for (String logicalId : ordered) {
            Map.Entry<String, StateSchema> entry = content.get(logicalId);
            out.add(bind(logicalId, entry.getKey(), entry.getValue()));
        }
        return out;
    }

    private Outcome allocate(String logicalId, String bedrockId, StateSchema schema) {
        Optional<Integer> sizeClass = pool.classFor(schema.size());
        if (sizeClass.isEmpty()) {
            return new Outcome.Exhausted(logicalId, schema.size(), 1);
        }
        int chosen = sizeClass.get();
        java.util.BitSet taken = used.computeIfAbsent(chosen, ignored -> new java.util.BitSet());
        int index = taken.nextClearBit(0);
        if (index >= pool.capacity(chosen)) {
            return new Outcome.Exhausted(logicalId, chosen, 1);
        }
        taken.set(index);
        Binding binding =
                new Binding(logicalId, bedrockId, new BlockSlot(chosen, index), schema, List.of());
        byLogicalId.put(logicalId, binding);
        return new Outcome.Allocated(binding);
    }

    private void mark(BlockSlot slot) {
        used.computeIfAbsent(slot.sizeClass(), ignored -> new java.util.BitSet()).set(slot.index());
    }

    /** The pool this ledger's bindings require, for SC-120 §6.2's element-wise maximum. */
    public SlotPool requiredPool() {
        Map<Integer, Integer> needed = new TreeMap<>();
        for (Binding binding : byLogicalId.values()) {
            needed.merge(binding.slot().sizeClass(), binding.slot().index() + 1, Math::max);
        }
        return new SlotPool(needed);
    }
}
