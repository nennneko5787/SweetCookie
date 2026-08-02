package net.nennneko5787.lepus.core.format.ir.geometry;

import java.util.List;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.value.Vec3f;

/**
 * How a model is placed when it is drawn as an <b>item</b> rather than as a block. SC-180 §3.6.
 *
 * <p>A model in the world sits where the block is. The same model in a hotbar slot, in a hand, on a
 * head or in an item frame has no block to sit in, so both editions carry a per-context transform
 * that says where to put it — {@code item_display_transforms} in a Bedrock geometry, {@code display}
 * in a Java model. The two agree on the context names, on the units and on the fields, which is why
 * this is a record with three vectors and no conversion in it.
 *
 * <p><b>Dropping these is visible and nothing else about the model is.</b> A block with no display
 * transform draws correctly everywhere the world draws it and wrongly everywhere an item is drawn,
 * which reads as "the icon is mirrored" rather than as "a component is missing" — the pack author
 * chose an angle and the icon is at Java's default one, roughly a quarter turn away.
 *
 * @param rotation    degrees, per axis
 * @param translation in 1/16 of a block, as both editions state it
 * @param scale       a multiplier per axis, 1 meaning unchanged
 */
@SpecImpl("SC-180#geometry/item_display_transforms")
public record ItemDisplay(Vec3f rotation, Vec3f translation, Vec3f scale) {

    /** What a context that declares nothing is: the model where the renderer would have put it. */
    public static final ItemDisplay NONE = new ItemDisplay(Vec3f.ZERO, Vec3f.ZERO, Vec3f.ONE);

    /**
     * The contexts, spelled as both editions spell them.
     *
     * <p>The same eight names in the same JSON, which is not a coincidence — Bedrock took the idea
     * and the vocabulary from Java. So the mapping is the identity and there is no table to get
     * wrong; a name outside this list is reported and dropped rather than passed through, because
     * Java's model loader silently ignores a context it does not know and a typo would then be
     * invisible in both the file and the game.
     */
    public static final List<String> CONTEXTS = List.of(
            "thirdperson_righthand", "thirdperson_lefthand",
            "firstperson_righthand", "firstperson_lefthand",
            "gui", "head", "ground", "fixed");

    /** True when this places the model exactly where it would have been anyway. */
    public boolean isIdentity() {
        return NONE.equals(this);
    }
}
