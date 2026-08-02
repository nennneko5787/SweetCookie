package net.nennneko5787.lepus.runtime.registry;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.nennneko5787.lepus.core.format.ir.item.ItemProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.Lepus;
import net.nennneko5787.lepus.core.registry.BlockLedger;

/**
 * The one item Lepus registers. SC-120 §4.
 *
 * <p>Every Bedrock item — and every bound block's item form — is a stack of <b>this</b>, carrying its
 * Bedrock identity in {@code minecraft:custom_data}. Nothing is registered per add-on, so items
 * hot-plug completely and constitution rule 7 holds: this item is not named after a Bedrock feature
 * because it is not any Bedrock feature in particular.
 *
 * <p>A dedicated carrier rather than a vanilla base, per SC-120 §4: a custom item built on
 * {@code minecraft:paper} would silently participate in vanilla recipes and tags and craft into a
 * book. This one is in no tag and no recipe, so vanilla never touches it.
 *
 * <p>Stack merging comes out right for free — {@code custom_data} takes part in component equality,
 * so two stacks of different Bedrock content never merge and two of the same always do.
 */
@SpecImpl({"SC-120", "SC-170"})
public final class AddonItem extends Item {

    /** The key inside {@code custom_data}. One object, so nothing of ours collides with anything. */
    public static final String TAG = "lepus";

    /** The logical identifier, which is the only kind that may appear anywhere but a chunk. */
    private static final String ID = "id";

    public AddonItem(Properties properties) {
        super(properties);
    }

    /**
     * A stack of one bound block.
     *
     * <p>Carries three components: the identity, the name to show, and the model to draw. The last
     * two are per stack rather than per item because there is one item and thousands of possible
     * contents — which is the whole point of the carrier, and is only expressible because 1.20.5
     * made both of them data components.
     */
    public static ItemStack of(String logicalId, Component name, Identifier model) {
        return of(logicalId, name, model, ItemProfile.NONE);
    }

    /**
     * A stack, with the Bedrock item's components written onto it. SC-170 §2.
     *
     * <p><b>Per stack, because Java asks the stack.</b> {@code ItemStack.getMaxStackSize} and its
     * neighbours read data components and never consult the item class, so there is no way to answer
     * these from the carrier — one registered item cannot have 106 different stack limits, but 106
     * stacks of it can.
     *
     * <p>The cost is that a stack sitting in a chest keeps the values it was made with. SC-120 §5
     * asks that such a stack <b>survive</b> a pack being disabled or updated, and it does; it may be
     * out of date until it is remade. Rewriting them as the stack ticks would fix that and break
     * something worse — components take part in stack equality, so a stack being corrected would
     * stop merging with its own kind mid-inventory.
     */
    public static ItemStack of(String logicalId, Component name, Identifier model,
            ItemProfile profile) {
        ItemStack stack = new ItemStack(LepusItems.item());
        CompoundTag tag = new CompoundTag();
        tag.putString(ID, logicalId);
        CompoundTag root = new CompoundTag();
        root.put(TAG, tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        stack.set(DataComponents.ITEM_NAME, name);
        stack.set(DataComponents.ITEM_MODEL, model);
        apply(stack, profile);
        return stack;
    }

    /**
     * The equipment asset every wearable add-on item names. SC-170 §5.
     *
     * <p><b>It exists to be named, not to draw.</b> Vanilla only puts a worn stack on the render
     * state — {@code headEquipment} and its three neighbours — when the stack's {@code equippable}
     * carries an asset id: {@code HumanoidMobRenderer.getEquipmentIfRenderable} asks
     * {@code HumanoidArmorLayer.shouldRender}, which asks {@code assetId().isPresent()}. Ours
     * carried none, so those fields stayed {@code ItemStack.EMPTY} and the layer that draws worn
     * attachables was handed nothing, every frame, for every player. The reported symptom was "a
     * worn halo shows a flat texture and no model" — that flat texture being vanilla's
     * {@code CustomHeadLayer}, which puts an item's own model on a head and had been the only thing
     * drawing at all.
     *
     * <p>The asset it points at declares one layer of a type no humanoid renders, because
     * {@code EquipmentClientInfo}'s codec rejects an empty layer map and anything a humanoid DOES
     * render would put a second, flat armour piece over the model. Bedrock draws the attachable and
     * nothing else.
     */
    private static final ResourceKey<EquipmentAsset> EQUIPMENT_ASSET = ResourceKey.create(
            EquipmentAssets.ROOT_ID,
            Identifier.fromNamespaceAndPath(Lepus.MOD_ID, "attachable"));

    /**
     * Whether this stack's attachable belongs to an armour slot rather than to a hand. SC-170 §5.
     *
     * <p><b>Bedrock shows a worn attachable only once it is worn.</b> Holding the armour piece shows
     * its icon and nothing else — verified against a Bedrock client. Drawing it from the hand as
     * well put a whole character on the player for merely selecting the item in the hotbar, and
     * then a second copy of her once it was equipped.
     *
     * <p>Asked of the STACK's {@code minecraft:equippable}, which is where {@code minecraft:wearable}
     * landed (SC-170 §2), rather than of the attachable: an attachable file says nothing about which
     * slot it is for. A pack that declares no wearable slot is held, which is the common case and the
     * safe answer.
     */
    public static boolean wornRatherThanHeld(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        // Named rather than asked for an "is armour" flag: the slot enum's grouping methods differ
        // between the two supported versions and these two constants do not.
        return equippable != null
                && equippable.slot() != EquipmentSlot.MAINHAND
                && equippable.slot() != EquipmentSlot.OFFHAND;
    }

    /**
     * SC-170 §2's mapping, applied. The transcription half — every decision is in {@link ItemProfile}.
     *
     * <p><b>Only what the pack asked for is written.</b> A component set unconditionally would
     * override whatever the carrier item already had with a value nobody in the pack chose, so an
     * absent Bedrock component leaves the Java one alone rather than setting it to a default.
     */
    private static void apply(ItemStack stack, ItemProfile profile) {
        profile.maxStackSize().ifPresent(size -> stack.set(DataComponents.MAX_STACK_SIZE, size));
        profile.maxDurability().ifPresent(max -> {
            stack.set(DataComponents.MAX_DAMAGE, max);
            // Fresh, not broken. Java takes an absent DAMAGE as zero, but a stack that carries
            // MAX_DAMAGE and nothing else has no durability bar to show until it is first damaged.
            stack.set(DataComponents.DAMAGE, 0);
        });
        profile.enchantValue()
                .ifPresent(value -> stack.set(DataComponents.ENCHANTABLE, new Enchantable(value)));
        profile.glint()
                .ifPresent(on -> stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, on));
        profile.javaEquipmentSlot().flatMap(AddonItem::slotNamed).ifPresent(slot -> {
            stack.set(DataComponents.EQUIPPABLE,
                    Equippable.builder(slot).setAsset(EQUIPMENT_ASSET).build());
            profile.protection().filter(armour -> armour != 0)
                    .ifPresent(armour -> stack.set(DataComponents.ATTRIBUTE_MODIFIERS,
                            armourModifiers(slot, armour)));
        });
    }

    /**
     * Bedrock's armour value as a Java one, on the slot that wears it.
     *
     * <p><b>Taken as the same number, which is not established.</b> Bedrock states protection on its
     * own scale and Java's `armor` attribute is points of a twenty-point bar; the two are close
     * enough that vanilla-equivalent armour lines up, and nothing here has been measured against a
     * Bedrock client. It is recorded as a fidelity note rather than hidden in this method.
     */
    private static ItemAttributeModifiers armourModifiers(EquipmentSlot slot, int armour) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR,
                        new AttributeModifier(
                                Identifier.fromNamespaceAndPath(Lepus.MOD_ID,
                                        "armor." + slot.getName()),
                                armour,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.bySlot(slot))
                .build();
    }

    /**
     * The slot of that name, or empty when Java has none.
     *
     * <p>Empty rather than a guess: {@link ItemProfile#javaEquipmentSlot} has already refused the
     * Bedrock names Java cannot express, and this refuses anything left. An item that cannot be
     * worn is still an item — constitution rule 5 — where a wrong slot would put a hat on somebody's
     * feet and look deliberate.
     */
    private static Optional<EquipmentSlot> slotNamed(String name) {
        return switch (name) {
            case "head" -> Optional.of(EquipmentSlot.HEAD);
            case "chest" -> Optional.of(EquipmentSlot.CHEST);
            case "legs" -> Optional.of(EquipmentSlot.LEGS);
            case "feet" -> Optional.of(EquipmentSlot.FEET);
            case "offhand" -> Optional.of(EquipmentSlot.OFFHAND);
            default -> Optional.empty();
        };
    }

    /** What Bedrock content this stack is, if it is any. */
    public static Optional<String> logicalIdOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }
        CompoundTag root = data.copyTag();
        return root.getCompound(TAG)
                .flatMap(tag -> tag.getString(ID))
                .filter(id -> !id.isEmpty());
    }

    /**
     * Places the bound block. SC-120 §6.
     *
     * <p>Through {@link BlockPlaceContext} rather than by writing to the clicked position directly,
     * so that clicking the side of a block places beside it, clicking tall grass replaces it, and
     * placing inside oneself is refused — all of which are vanilla's rules and none of which are
     * worth restating badly.
     *
     * <p>An unbound identity is not an error the player can act on and not a crash: the stack simply
     * does nothing. It is what a stack of a disabled pack's block is, and SC-120 §5 requires that to
     * survive rather than to vanish.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Optional<String> logicalId = logicalIdOf(context.getItemInHand());
        if (logicalId.isEmpty()) {
            return InteractionResult.PASS;
        }
        Optional<BlockLedger.Binding> binding =
                WorldLedger.current().flatMap(ledger -> ledger.binding(logicalId.get()));
        Optional<PoolBlock> block = binding.flatMap(at -> Lepus.blockPool().block(at.slot()));
        if (block.isEmpty()) {
            return InteractionResult.PASS;
        }

        BlockPlaceContext placement = new BlockPlaceContext(context);
        if (!placement.canPlace()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos pos = placement.getClickedPos();
        // The state the placement puts it in, not state zero. Bedrock's placement traits are states
        // like any other, so a block with minecraft:placement_direction placed always in state zero
        // always faces the same way however it was put down.
        BlockState state = block.get().stateOf(
                PlacementStates.indexFor(binding.get().schema(), placement));
        // canPlace() asks only whether the BLOCK there can be replaced. It says nothing about what
        // is standing there, so on its own it will happily build a block inside the player - and
        // then another on top of that, because the first one pushed nobody out of the way.
        //
        // isUnobstructed is the check vanilla's BlockItem makes and the one that was missing. An
        // EMPTY collision context is deliberate: it asks "does anything collide with this shape",
        // which is the question, rather than "can this particular entity fit", which would let a
        // player in spectator or a mount's rider place inside themselves.
        if (!state.canSurvive(level, pos)
                || !level.isUnobstructed(state, pos, CollisionContext.empty())) {
            return InteractionResult.PASS;
        }
        if (!level.setBlock(pos, state, 3)) {
            return InteractionResult.PASS;
        }
        if (context.getPlayer() == null || !context.getPlayer().hasInfiniteMaterials()) {
            context.getItemInHand().shrink(1);
        }
        level.playSound(context.getPlayer(), pos, state.getSoundType().getPlaceSound(),
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }
}
