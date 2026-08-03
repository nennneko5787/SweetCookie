package net.nennneko5787.lepus.runtime.registry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.lepus.core.format.render.AttachablePoser;
import net.nennneko5787.lepus.core.format.render.Mat4f;
import net.nennneko5787.lepus.core.format.render.Playback;
import net.nennneko5787.lepus.core.molang.MolangContext;

/**
 * The 3D model each held item draws as, resolved. SC-170 §5.
 *
 * <p>{@link BoundBlocks}' counterpart for the thing an item looks like <b>in a hand</b> rather than
 * in a slot. Keyed by logical identifier, because that is what a stack carries (SC-120 §4) and
 * therefore the only thing the renderer has when it is handed one.
 *
 * <p><b>The bind pose is computed once, here, and not per frame.</b> A bone chain is the same every
 * frame until an animation moves it, and an item in a hand is drawn sixty times a second — twice,
 * in first and third person. Recomputing thirty bone matrices per draw would be work whose answer
 * never changes. Animation will add a per-frame layer on top (stage C); this stays the base it
 * starts from.
 */
@SpecImpl({"SC-170#attachable/geometry", "SC-170#attachable/textures", "SC-170#attachable/item"})
public final class BoundAttachables {

    /**
     * One item's held model.
     *
     * @param geometry   the Bedrock model, unconverted — the renderer converts once, at the edge
     * @param texture    where the generated pack serves its texture
     * @param bindPose   every bone's transform with no animation applied
     * @param animations what plays, in the order {@code scripts.animate} lists it
     */
    public record Bound(GeometryIr geometry, Identifier texture, AttachablePoser poser,
            boolean inFirstPerson) {

        public static Bound of(GeometryIr geometry, Identifier texture, AttachablePoser poser) {
            return new Bound(geometry, texture, poser, true);
        }

        /**
         * One dressing a <b>vanilla</b> item, which is third person only. SC-170 §5.2.
         *
         * <p>Not a policy and not a limitation of this build: a Bedrock client draws nothing in first
         * person for an attachable whose identifier is a vanilla item, measured with a probe on
         * {@code minecraft:stick} and isolated against a custom item held the same way. The corpus's
         * one such pack writes four first-person animations that Bedrock never plays; playing them
         * here would be inventing a frame its author never saw.
         */
        public static Bound onVanillaItem(GeometryIr geometry, Identifier texture,
                AttachablePoser poser) {
            return new Bound(geometry, texture, poser, false);
        }

        /**
         * Every bone's transform at a moment, for the context this frame is in.
         *
         * <p>The context decides <b>which animations play at all</b>, not merely what they evaluate
         * to: {@code scripts.animate} conditions on {@code c.is_first_person}, so the same item is
         * two different poses depending on who is looking. See {@code AttachablePoser}.
         */
        public Map<String, Mat4f> poseAt(Playback playback, MolangContext context) {
            return poser.at(playback, context, Map.of());
        }

        /**
         * As above, with the bones the wearer's own skeleton drives. SC-180 §4.2.
         *
         * <p>A halo is a cube-less {@code head} bone at the player's head pivot with the ring
         * beneath it; nothing in the pack turns that bone, because Bedrock turns it.
         */
        public Map<String, Mat4f> poseAt(Playback playback, MolangContext context,
                Map<String, Mat4f> skeleton) {
            return poser.at(playback, context, skeleton);
        }

    }

    private static volatile Map<String, Bound> byLogicalId = Map.of();

    private BoundAttachables() {
    }

    /**
     * Replaces the whole snapshot.
     *
     * <p>Volatile and whole-snapshot for the same reason the block bindings are: binding runs on the
     * server thread and rendering does not, and a map half-replaced would draw one item's model on
     * another's stack for as long as it took to finish.
     */
    public static void replace(Map<String, Bound> bound) {
        byLogicalId = Map.copyOf(bound);
    }

    /** What this stack's content is held as, if anything. */
    public static Optional<Bound> at(String logicalId) {
        return Optional.ofNullable(byLogicalId.get(logicalId));
    }

    /**
     * What a stack is drawn as, whichever kind of identity it carries. SC-170 §5.2.
     *
     * <p><b>Logical identity first, registry name only in its absence.</b> An add-on stack answers
     * the first and never reaches the second, so a carrier item whose content is unbound cannot fall
     * through to whatever a pack happened to bind to {@code lepus:item}. A vanilla stack has no
     * logical identity at all, and its registry name is the key a pack naming
     * {@code minecraft:totem_of_undying} bound under.
     */
    public static Optional<Bound> of(ItemStack stack) {
        if (stack == null || stack.isEmpty() || byLogicalId.isEmpty()) {
            return Optional.empty();
        }
        return AddonItem.logicalIdOf(stack)
                .or(() -> Optional.ofNullable(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                        .map(Identifier::toString))
                .flatMap(BoundAttachables::at);
    }

    /** True when any item has one, so a renderer can be skipped entirely when none do. */
    public static boolean isEmpty() {
        return byLogicalId.isEmpty();
    }

    public static void clear() {
        byLogicalId = new LinkedHashMap<>();
    }
}
