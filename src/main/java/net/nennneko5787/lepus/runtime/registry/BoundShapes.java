package net.nennneko5787.lepus.runtime.registry;

import java.util.Optional;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.block.BlockBox;

/**
 * Turns a read Bedrock box into the shape Minecraft collides and draws outlines against.
 * SC-150 §4.
 *
 * <p>Everything about <i>reading</i> a box is in {@code core}'s {@link BlockBox}; all that is left
 * here is one call into {@code Shapes}, which is why this class is four lines of substance. The
 * split is deliberate: the arithmetic that can be got wrong — Bedrock measuring X and Z from the
 * block's centre where Java measures from its corner — is testable in milliseconds with no
 * Minecraft, and this side only divides by sixteen.
 */
@SpecImpl({"SC-150#minecraft:collision_box", "SC-150#minecraft:selection_box"})
final class BoundShapes {

    private BoundShapes() {
    }

    /**
     * The shape for one box.
     *
     * <p>Both ways of having no shape arrive at {@code Shapes.empty()}: the component said
     * {@code false}, or it described a box with no volume. They mean different things to a pack
     * author and the same thing to a player.
     *
     * <p>Called at bind time, once per state, never from a collision query — see {@link BoundBlocks}
     * on why anything else would be indefensible.
     */
    static VoxelShape of(Optional<BlockBox> box) {
        return box.filter(b -> !b.isEmpty())
                .map(b -> b.isFullCube() ? Shapes.block() : Shapes.box(
                        b.minX() / 16.0, b.minY() / 16.0, b.minZ() / 16.0,
                        b.maxX() / 16.0, b.maxY() / 16.0, b.maxZ() / 16.0))
                .orElseGet(Shapes::empty);
    }
}
