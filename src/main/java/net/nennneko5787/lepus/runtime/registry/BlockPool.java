package net.nennneko5787.lepus.runtime.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.registry.BlockSlot;
import net.nennneko5787.lepus.core.registry.IdMapper;
import net.nennneko5787.lepus.core.registry.SlotPool;

/**
 * Registers the anonymous block pool at startup. SC-120 §6.
 *
 * <p>This is the one place Lepus touches a Minecraft registry, and it does so <b>before the
 * registry freezes and without knowing about a single Bedrock feature</b>. Every block registered
 * here is named after its size class and its index and nothing else, so that add-ons can attach and
 * detach per world afterwards with no registration at all (constitution rule 7, ADR-0007).
 *
 * <p>Registration is version-free and loader-free: {@code Registry.register},
 * {@code IntegerProperty.create}, {@code Identifier.fromNamespaceAndPath},
 * {@code BlockBehaviour.Properties.of} and {@code Properties.setId} are byte-identical on both
 * supported Minecraft versions, so this lives in the shared source tree with no {@code //?}
 * comments in it.
 */
@SpecImpl("SC-120")
public final class BlockPool {

    private final Map<BlockSlot, PoolBlock> blocks;
    private final SlotPool pool;

    private BlockPool(SlotPool pool, Map<BlockSlot, PoolBlock> blocks) {
        this.pool = pool;
        this.blocks = Map.copyOf(blocks);
    }

    /**
     * Registers every reserved block.
     *
     * <p><b>Call exactly once, during mod initialisation, before the block registry freezes.</b>
     * Iteration is by ascending size class then index so the registry receives them in a
     * deterministic order — registry order affects nothing on the wire (SC-270) but it affects what
     * a debug dump looks like, and a stable dump is worth having for free.
     */
    public static BlockPool register(SlotPool pool) {
        Map<BlockSlot, PoolBlock> registered = new LinkedHashMap<>();
        List<Integer> classes = new ArrayList<>(pool.capacities().keySet());
        classes.sort(null);

        for (int sizeClass : classes) {
            for (int index = 0; index < pool.capacity(sizeClass); index++) {
                BlockSlot slot = new BlockSlot(sizeClass, index);
                Identifier id = identifierOf(slot);
                // The id goes on the PROPERTIES, before the block is constructed, and the same id is
                // then used to register it. Block's constructor reads it and throws "Block id not
                // set" without it — which is what the first real client launch hit, because nothing
                // in a headless build ever constructs a Block.
                PoolBlock block = new PoolBlock(
                        properties().setId(ResourceKey.create(Registries.BLOCK, id)), slot);
                Registry.register(BuiltInRegistries.BLOCK, id, block);
                registered.put(slot, block);
            }
        }
        return new BlockPool(pool, registered);
    }

    /**
     * The registry name for a slot: {@code lepus:block_16/0037}.
     *
     * <p>Derived from {@link BlockSlot#toString()} rather than reformatted, so there is one spelling
     * of a slot in the project and it is the one the ledger writes.
     */
    static Identifier identifierOf(BlockSlot slot) {
        String name = slot.toString();
        int colon = name.indexOf(':');
        return Identifier.fromNamespaceAndPath(
                name.substring(0, colon), name.substring(colon + 1).toLowerCase(Locale.ROOT));
    }

    /**
     * What a pool block looks like before anything is bound to it.
     *
     * <p>Deliberately inert and deliberately unremarkable. Everything a Bedrock block component
     * controls — hardness, light, collision, map colour — is read through the live reference at
     * lookup time (SC-150 §1), so baking any of it into the registered properties would freeze the
     * one thing that has to stay hot-swappable.
     *
     * <p>The strength is not zero: an unbound slot, or one whose pack has been detached, becomes a
     * placeholder that must <b>not</b> be breakable by normal means (SC-120 §7), and a pack being
     * disabled must never destroy what it placed (constitution rule 5).
     */
    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .strength(-1.0f, 3600000.0f)
                // A FUNCTION, which is what SC-150 1 means by properties built from functions
                // closing over the live reference: light is asked for per state, every time, so a
                // pack bound after registration still lights the world.
                .lightLevel(BoundBlocks::lightOf)
                // Minecraft's per-state Cache holds a collision shape, and the context-free
                // getCollisionShape(level, pos) answers out of it when it exists. It is built by
                // BlockState.initCache, which vanilla runs once from Blocks' class initialiser -
                // before any world, and therefore before any pack is bound. A pool block that got
                // one would collide as a full cube forever after, whatever its collision_box said.
                //
                // Whether our states ever get one is a question about when a loader runs
                // registration relative to that class initialiser. That is not ours to answer and
                // it can change under us, so this does not depend on the answer: `dynamicShape` is
                // vanilla's own way of saying "ask the block, do not cache", and a slot whose shape
                // changes when a pack attaches is as dynamic as a shape gets.
                .dynamicShape()
                // SC-150 5.5. A model smaller than its block must not cull the faces behind it, and
                // whether it is smaller is not known here: the occlusion shape is baked once, before
                // any pack is bound, and there is no dynamicShape equivalent to opt out of it.
                //
                // The cost is that a bound block which IS a full cube no longer blocks light. That
                // is mild and visible - a brighter cave - against severe and confusing: terrain
                // disappearing behind a custom model.
                .noOcclusion()
                .sound(SoundType.STONE);
    }

    public SlotPool pool() {
        return this.pool;
    }

    /** The registered block for a slot, if that slot is inside the pool. */
    public Optional<PoolBlock> block(BlockSlot slot) {
        return Optional.ofNullable(blocks.get(slot));
    }

    /** How many blocks were registered. Worth logging: it is a real cost against the palette. */
    public int size() {
        return blocks.size();
    }

    /** Every slot, in registration order. */
    public List<BlockSlot> slots() {
        return List.copyOf(blocks.keySet());
    }

    /** True when {@code block} is one of ours. Cheap enough for a hot path. */
    public boolean isPoolBlock(Block block) {
        return block instanceof PoolBlock;
    }

    /** The namespace every pool block lives in. */
    public static String namespace() {
        return IdMapper.NAMESPACE;
    }
}
