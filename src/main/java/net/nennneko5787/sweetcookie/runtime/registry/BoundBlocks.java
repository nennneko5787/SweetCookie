package net.nennneko5787.sweetcookie.runtime.registry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.level.block.state.BlockState;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.ir.block.BlockPhysics;
import net.nennneko5787.sweetcookie.core.registry.BlockSlot;

/**
 * What each bound slot currently behaves like. SC-150 §1's live reference.
 *
 * <p>SC-150 §1 requires behaviour to be read through a reference that a reload can replace, rather
 * than baked into the registered block — that is the whole reason packs can attach and detach
 * without touching a registry. This is that reference: a snapshot replaced wholesale on every bind,
 * so a pool block asks it what it currently is and gets a different answer after a pack changes.
 *
 * <p><b>Resolved at bind time, not at access time</b>, also per §1. A permutation condition can only
 * see block state, so every state index has a fixed component set and there is nothing left to
 * decide when {@code getShape} runs. Evaluating Molang inside a collision query would be
 * indefensible; this is what makes sure nothing ever does.
 */
@SpecImpl("SC-150")
public final class BoundBlocks {

    /** One bound block, with its behaviour already resolved for every state index. */
    public record Bound(String logicalId, List<BlockPhysics> byStateIndex) {

        public Bound {
            byStateIndex = List.copyOf(byStateIndex);
        }

        /**
         * The behaviour of one state.
         *
         * <p>Out-of-range answers with the default rather than throwing. An index can outrun the
         * list when a pack shrinks its state list and a placed block has not been remapped yet, and
         * a block that behaves plainly for a moment beats a crash in a collision query.
         */
        public BlockPhysics at(int index) {
            return index >= 0 && index < byStateIndex.size()
                    ? byStateIndex.get(index)
                    : BlockPhysics.DEFAULT;
        }
    }

    private static Map<BlockSlot, Bound> bySlot = Map.of();

    private BoundBlocks() {
    }

    /** Replaces the whole snapshot. Called by {@link BlockBinding} after every bind. */
    public static void replace(Map<BlockSlot, Bound> bindings) {
        bySlot = Map.copyOf(bindings);
    }

    /** Forgets everything. A client between worlds must not answer with the last world's blocks. */
    public static void clear() {
        bySlot = Map.of();
    }

    public static Optional<Bound> at(BlockSlot slot) {
        // Null-checked, and not defensively: see physicsOf. Map.of() throws on a null key rather
        // than answering absent, so the guard has to be here as well as there.
        return slot == null ? Optional.empty() : Optional.ofNullable(bySlot.get(slot));
    }

    /**
     * What this block state currently behaves like, or empty when nothing is bound to it.
     *
     * <p>Empty is a real answer and not a failure: most of the 2,012 registered slots are unbound at
     * any moment, and an unbound slot is the placeholder SC-120 §7 describes — it must stay
     * unbreakable rather than fall back to something ordinary.
     *
     * <p><b>The slot can be null here, during registration.</b> {@code lightLevel} is a function and
     * Minecraft calls it while building a block's state definition — which happens inside
     * {@code Block}'s constructor, before the subclass has assigned any field. So every block asks
     * this question once with no slot yet, and answering with an exception would take the client
     * down on the first of 2,012. The headless registration test found it; a client would have found
     * it too, one round trip later.
     */
    public static Optional<BlockPhysics> physicsOf(BlockState state) {
        if (!(state.getBlock() instanceof PoolBlock pool) || pool.slot() == null) {
            return Optional.empty();
        }
        return at(pool.slot()).map(bound -> bound.at(pool.indexOf(state)));
    }

    /** Light level, for the function {@code Properties.lightLevel} holds. SC-150 §1. */
    public static int lightOf(BlockState state) {
        return physicsOf(state).map(BlockPhysics::lightEmission).orElse(0);
    }
}
