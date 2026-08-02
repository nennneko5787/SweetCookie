package net.nennneko5787.lepus.runtime.registry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.block.BlockPhysics;
import net.nennneko5787.lepus.core.format.text.DisplayNames;
import net.nennneko5787.lepus.core.registry.BlockSlot;

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

    /**
     * How one state index looks: the model file, and every texture file it names.
     *
     * <p>Both are held as finished bytes rather than as something to resolve later. The add-on
     * archives stay open for as long as the registry lives, so reading on demand would work — but
     * the resource manager asks on its own thread at its own time, and pinning an archive to that
     * is a lifetime nobody is tracking.
     *
     * @param modelJson    a complete Java block model, transpiled (SC-150 §5) or the cube fallback
     * @param textureFiles pack-relative path to PNG bytes, e.g. {@code textures/block/16_3_0.png}
     */
    public record Appearance(String modelJson, Map<String, byte[]> textureFiles) {

        public Appearance {
            textureFiles = Map.copyOf(textureFiles);
        }
    }

    /**
     * One bound block, with its behaviour already resolved for every state index.
     *
     * @param sound the Java sound group, resolved from what the pack declared. Per block rather than
     *              per state, because Bedrock declares it per block
     */
    public record Bound(String logicalId, List<BlockPhysics> byStateIndex,
            List<Appearance> appearanceByStateIndex,
            List<VoxelShape> collisionByStateIndex, List<VoxelShape> selectionByStateIndex,
            net.minecraft.world.level.block.SoundType sound) {

        public Bound {
            byStateIndex = List.copyOf(byStateIndex);
            appearanceByStateIndex = List.copyOf(appearanceByStateIndex);
            collisionByStateIndex = List.copyOf(collisionByStateIndex);
            selectionByStateIndex = List.copyOf(selectionByStateIndex);
        }

        /**
         * The usual way to build one: the shapes are <b>derived</b> from the physics rather than
         * passed alongside them, so the two cannot disagree about what a state index is.
         *
         * <p>The conversion happens here, at bind time, and not in {@code getShape}. A collision
         * query runs many times per tick per entity; building a {@code VoxelShape} inside one would
         * be paying for a decision that was already made when the pack was bound.
         */
        public static Bound of(String logicalId, List<BlockPhysics> byStateIndex,
                List<Appearance> appearances,
                net.minecraft.world.level.block.SoundType sound) {
            return new Bound(logicalId, byStateIndex, appearances,
                    byStateIndex.stream().map(p -> BoundShapes.of(p.collision())).toList(),
                    byStateIndex.stream().map(p -> BoundShapes.of(p.selection())).toList(),
                    sound);
        }

        /**
         * The collision shape of one state, or the full block when the index is out of range.
         *
         * <p>Out of range answers with a whole block rather than throwing, for the reason
         * {@link #at} gives: an index can outrun the list while a shrunken pack's placed blocks
         * wait to be remapped, and a block that is briefly solid beats a crash inside movement.
         */
        public VoxelShape collisionAt(int index) {
            return index >= 0 && index < collisionByStateIndex.size()
                    ? collisionByStateIndex.get(index)
                    : Shapes.block();
        }

        /** The outline shape of one state. Out of range is the full block, as with {@link #collisionAt}. */
        public VoxelShape selectionAt(int index) {
            return index >= 0 && index < selectionByStateIndex.size()
                    ? selectionByStateIndex.get(index)
                    : Shapes.block();
        }

        /** How one state looks, or empty when the index is out of range. */
        public Optional<Appearance> appearanceAt(int index) {
            return index >= 0 && index < appearanceByStateIndex.size()
                    ? Optional.of(appearanceByStateIndex.get(index))
                    : Optional.empty();
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
    private static List<BlockSlot> menuOrder = List.of();

    private BoundBlocks() {
    }

    /** Replaces the whole snapshot. Called by {@link BlockBinding} after every bind. */
    public static void replace(Map<BlockSlot, Bound> bindings) {
        replace(bindings, List.copyOf(bindings.keySet()));
    }

    /**
     * Replaces the snapshot and the order the creative tab lists it in.
     *
     * @param order the bound slots, in the order SC-170 §6 asks for — by pack, then by Bedrock's own
     *              menu_category. Slot order would be allocation order, which is the order packs
     *              happened to be bound in across the world's whole history and means nothing to a
     *              player.
     */
    public static void replace(Map<BlockSlot, Bound> bindings, List<BlockSlot> order) {
        bySlot = Map.copyOf(bindings);
        menuOrder = List.copyOf(order);
    }

    /** The bound slots, in creative-menu order. */
    public static List<BlockSlot> inMenuOrder() {
        return menuOrder;
    }

    /** Forgets everything. A client between worlds must not answer with the last world's blocks. */
    public static void clear() {
        bySlot = Map.of();
        menuOrder = List.of();
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

    /**
     * What to call this content, in whatever language the client is set to.
     *
     * <p>A <b>key</b>, not a string: the translations ship in the generated resource pack and the
     * client resolves them, so two players in one world each read their own language. Resolving it
     * here would bake one language into the item for everybody.
     *
     * <p>The fallback is the identifier made readable, used when no enabled pack translated this
     * into anything. Java would otherwise show the raw key, which looks like a bug rather than like
     * a pack that shipped no {@code texts/} folder.
     */
    public static net.minecraft.network.chat.Component nameOf(String logicalId) {
        return net.minecraft.network.chat.Component.translatableWithFallback(
                DisplayNames.javaKey(logicalId), DisplayNames.readable(logicalId));
    }

    /**
     * What this block sounds like, or stone when nothing is bound to it.
     *
     * <p>Stone for an unbound slot is what the registered properties already say, so a placeholder
     * keeps sounding exactly as it did before anything claimed the slot.
     */
    public static net.minecraft.world.level.block.SoundType soundOf(BlockState state) {
        if (!(state.getBlock() instanceof PoolBlock pool) || pool.slot() == null) {
            return net.minecraft.world.level.block.SoundType.STONE;
        }
        return at(pool.slot()).map(Bound::sound)
                .orElse(net.minecraft.world.level.block.SoundType.STONE);
    }

    /** Light level, for the function {@code Properties.lightLevel} holds. SC-150 §1. */
    public static int lightOf(BlockState state) {
        return physicsOf(state).map(BlockPhysics::lightEmission).orElse(0);
    }

    /**
     * What this state collides with, or the whole block when nothing is bound to it.
     *
     * <p>A whole block for an unbound slot is the same answer {@link BlockPool} gives everywhere
     * else about a placeholder (SC-120 §7): it stays solid and unbreakable, so a detached pack's
     * blocks remain something a player can stand on rather than falling through the world.
     */
    public static VoxelShape collisionOf(BlockState state) {
        return shapeOf(state, true);
    }

    /** What the cursor can target on this state, or the whole block when nothing is bound. */
    public static VoxelShape selectionOf(BlockState state) {
        return shapeOf(state, false);
    }

    private static VoxelShape shapeOf(BlockState state, boolean collision) {
        // Not routed through physicsOf: that returns the state's components, and the shapes were
        // converted from them once at bind time. Going back to the components here would rebuild a
        // VoxelShape inside a collision query, which is the one thing SC-150 §1 forbids.
        if (!(state.getBlock() instanceof PoolBlock pool) || pool.slot() == null) {
            return Shapes.block();
        }
        return at(pool.slot())
                .map(bound -> collision
                        ? bound.collisionAt(pool.indexOf(state))
                        : bound.selectionAt(pool.indexOf(state)))
                .orElseGet(Shapes::block);
    }
}
