package net.nennneko5787.lepus.runtime.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.registry.BlockSlot;

/**
 * One anonymous block of the slot pool. SC-120 §6.1.
 *
 * <p>A pool block of size class <i>N</i> carries a single {@link IntegerProperty} named {@code i}
 * over {@code 0 .. N-1}, and nothing else. It is <b>not</b> named after any Bedrock feature and has
 * no idea which Bedrock block is bound to it: that mapping lives in the world ledger, and the block
 * reads its behaviour through a live reference so that a pack reload changes what it does without
 * re-registering anything (constitution rule 7).
 *
 * <p>Giving a Bedrock block one Java property per Bedrock state was the alternative, and it was
 * rejected deliberately (SC-120 §6.1): it trades readable debug output for the ability to attach and
 * detach packs at runtime, which is the whole point of the design.
 */
@SpecImpl("SC-120")
public final class PoolBlock extends Block {

    /**
     * The property being built, handed to {@link #createBlockStateDefinition} across the superclass
     * constructor.
     *
     * <p>Java calls an overridable method from a superclass constructor <em>before</em> the subclass
     * has initialised any field, so {@code createBlockStateDefinition} cannot read
     * {@code this.index}. This is a Java initialisation-order fact rather than anything about
     * Minecraft, and the thread-local is the standard way around it. It is set immediately before
     * {@code super(...)} and cleared immediately after, so nothing outside one constructor call can
     * observe it.
     */
    private static final ThreadLocal<IntegerProperty> UNDER_CONSTRUCTION = new ThreadLocal<>();

    /**
     * One property instance per size class, shared by every block of that class.
     *
     * <p><b>Sharing is required, not an optimisation.</b> {@code StateHolder} keys its values with a
     * {@code Reference2ObjectArrayMap} — an identity map — so a property is found only by being the
     * same object, never by being equal to one. Building a second {@code IntegerProperty} with the
     * same name and range gives an object that is {@code equals} to the first and useless against a
     * state built from it: {@code setValue} reports "does not exist in Block", which is exactly what
     * the second real client launch hit.
     *
     * <p>Vanilla shares its own properties the same way, as constants on
     * {@code BlockStateProperties}. Sharing across blocks is normal; a state definition holds the
     * property, it does not own it.
     */
    private static final Map<Integer, IntegerProperty> INDEX_PROPERTIES = new ConcurrentHashMap<>();

    private final BlockSlot slot;
    private final int sizeClass;
    private final IntegerProperty index;

    /**
     * @param sizeClass how many states this block has; a power of two. A class of 1 gets no
     *     property at all, because a property over a single value is not expressible and would cost
     *     a palette entry for nothing.
     */
    public PoolBlock(BlockBehaviour.Properties properties, BlockSlot slot) {
        super(begin(properties, slot.sizeClass()));
        this.slot = slot;
        int sizeClass = slot.sizeClass();
        this.sizeClass = sizeClass;
        this.index = sizeClass > 1 ? indexProperty(sizeClass) : null;
        UNDER_CONSTRUCTION.remove();
        if (index != null) {
            registerDefaultState(stateDefinition.any().setValue(index, 0));
        }
    }

    private static BlockBehaviour.Properties begin(
            BlockBehaviour.Properties properties, int sizeClass) {
        UNDER_CONSTRUCTION.set(sizeClass > 1 ? indexProperty(sizeClass) : null);
        return properties;
    }

    /** The property for a size class. The same object every time — see {@link #INDEX_PROPERTIES}. */
    private static IntegerProperty indexProperty(int sizeClass) {
        return INDEX_PROPERTIES.computeIfAbsent(
                sizeClass, states -> IntegerProperty.create("i", 0, states - 1));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState> builder) {
        IntegerProperty property = UNDER_CONSTRUCTION.get();
        if (property != null) {
            builder.add(property);
        }
    }

    /** Which reserved slot this is. The ledger binds a Bedrock block to it; SC-120 6.1. */
    public BlockSlot slot() {
        return slot;
    }

    /**
     * The state index this BlockState encodes.
     *
     * <p>Zero for a size-one class, which carries no property at all - it has exactly one state and
     * that state is index zero.
     */
    public int indexOf(net.minecraft.world.level.block.state.BlockState state) {
        return index == null ? 0 : state.getValue(index);
    }

    /**
     * How hard this block currently is to mine. SC-150 1.
     *
     * <p>Overridden rather than baked, because what is bound to a slot changes without the block
     * being re-registered - which is the entire point of the pool. An UNBOUND slot falls through to
     * super and stays unbreakable (SC-120 7): a placeholder must not become minable just because
     * nothing has claimed it.
     *
     * <p>The 30 is vanilla constant for "holding the right tool". Bedrock states a time and Java
     * states a hardness a tool speed divides into, so tool correctness has no Bedrock counterpart to
     * read; treating every tool as correct is the approximation, and it is the one to revisit when
     * mining speed is measured against Bedrock rather than eyeballed.
     */
    @Override
    protected float getDestroyProgress(net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
        var physics = BoundBlocks.physicsOf(state);
        if (physics.isEmpty()) {
            return super.getDestroyProgress(state, player, level, pos);
        }
        if (physics.get().unbreakable()) {
            return 0.0f;
        }
        float hardness = physics.get().hardness();
        return hardness <= 0.0f ? 1.0f : player.getDestroySpeed(state) / hardness / 30.0f;
    }

    /**
     * The outline the cursor draws on this block. SC-150 §4.
     *
     * <p>Bedrock's {@code selection_box}. Kept separate from {@link #getCollisionShape} because
     * Minecraft falls the second back to the first by default, and a block that can be walked
     * through but still targeted — Bedrock's {@code "collision_box": false} — is a shape a pack
     * cannot express if the two share one answer.
     */
    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return BoundBlocks.selectionOf(state);
    }

    /**
     * What this block is solid against. SC-150 §4, Bedrock's {@code collision_box}.
     *
     * <p>The shape is looked up, never built: {@link BoundBlocks} converted every state's box once
     * when the pack was bound, which is what keeps this method a map lookup on a path that runs
     * many times per tick per entity.
     */
    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return BoundBlocks.collisionOf(state);
    }

    /**
     * What the middle mouse button puts in your hand. SC-120 §4.
     *
     * <p>The carrier item carrying this block's LOGICAL identity — never the slot. A slot appears in
     * chunk storage and the ledger and nowhere else (constitution rule 12), and an item is somewhere
     * else: it is picked up, dropped, put in a chest, and read by anything that reads a stack.
     *
     * <p>An unbound slot answers with nothing, which is what picking a placeholder should do.
     */
    @Override
    protected net.minecraft.world.item.ItemStack getCloneItemStack(
            net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state, boolean includeData) {
        return slot == null
                ? net.minecraft.world.item.ItemStack.EMPTY
                : BoundBlocks.at(slot)
                        .map(bound -> AddonItem.of(bound.logicalId(),
                                BoundBlocks.nameOf(bound.logicalId()),
                                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                        BlockPool.namespace(), BlockBinding.itemModelOf(slot))))
                        .orElse(net.minecraft.world.item.ItemStack.EMPTY);
    }

    /**
     * What this block sounds like when placed, broken and walked on. SC-150 §4.
     *
     * <p>Overridden rather than baked for the reason every other behaviour here is: the registered
     * properties are filled in before any pack exists, so a fixed sound would make every add-on
     * block in the game sound like stone however its pack declared it.
     */
    @Override
    protected net.minecraft.world.level.block.SoundType getSoundType(
            net.minecraft.world.level.block.state.BlockState state) {
        return BoundBlocks.soundOf(state);
    }

    /** How many states this block has. Equal to its size class. */
    public int sizeClass() {
        return sizeClass;
    }

    /**
     * The block state carrying one state index.
     *
     * <p>Clamped rather than trusted: an index is computed from a schema that a pack can change
     * between loads, and {@code setValue} on a value outside the property's range throws. A block
     * placed in its first state beats a crash in the middle of placing it.
     */
    public net.minecraft.world.level.block.state.BlockState stateOf(int stateIndex) {
        if (index == null) {
            return defaultBlockState();
        }
        return defaultBlockState().setValue(index, Math.max(0, Math.min(sizeClass - 1, stateIndex)));
    }

    /** The slot index property, or {@code null} for a size class of 1. */
    public IntegerProperty indexProperty() {
        return index;
    }
}
