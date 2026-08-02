package net.nennneko5787.lepus.runtime.registry;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.nennneko5787.lepus.Lepus;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.registry.BlockSlot;
import net.nennneko5787.lepus.platform.CreativeTabs;

/**
 * Registers the carrier item and the creative tab. SC-120 §4, SC-170 §6.
 *
 * <p>Two registry entries for the whole project, both anonymous, both created before the registries
 * freeze. Neither is named after a Bedrock feature (constitution rule 7): one is "the item" and one
 * is "the tab", and what they hold is decided per world afterwards.
 */
@SpecImpl({"SC-120", "SC-170"})
public final class LepusItems {

    private static Item item;

    private LepusItems() {
    }

    /**
     * Registers both.
     *
     * <p><b>Call exactly once during mod initialisation.</b> The tab is registered here and its
     * contents are not: a tab is a registry entry and registries freeze before any world exists,
     * while packs are enabled per world afterwards. Fixed object, moving contents — which is the
     * same trade SC-120 §6 makes for blocks, for the same reason.
     */
    public static void register() {
        Identifier itemId = Identifier.fromNamespaceAndPath(Lepus.MOD_ID, "item");
        item = Registry.register(BuiltInRegistries.ITEM, itemId,
                new AddonItem(new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, itemId))));

        Identifier tabId = Identifier.fromNamespaceAndPath(Lepus.MOD_ID, "addons");
        // Through the platform, because the two loaders disagree about who places a tab: vanilla
        // makes the caller name a row and a column, and NeoForge places mod tabs itself and
        // deprecates saying otherwise. Naming a position there put this tab underneath a vanilla
        // one — registered, built, filled and invisible.
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabId, CreativeTabs.builder()
                .title(Component.literal("Lepus"))
                .icon(LepusItems::icon)
                // Asked for every time the tab is rebuilt, which the client does when it loads a
                // world and when resources reload - and binding a pack already forces the second.
                // So enabling a pack fills the tab without anything here scheduling it.
                .displayItems((parameters, output) -> contents().forEach(output::accept))
                .build());
    }

    /** The registered carrier. */
    public static Item item() {
        if (item == null) {
            throw new IllegalStateException("the carrier item is registered during mod init");
        }
        return item;
    }

    /**
     * The tab's icon.
     *
     * <p>The first bound block when there is one, so the tab looks like what is in it, and a plain
     * carrier stack otherwise. An empty tab with a missing-model icon is what a user sees before
     * they enable anything, and it should not look broken.
     */
    private static ItemStack icon() {
        List<ItemStack> contents = contents();
        return contents.isEmpty() ? new ItemStack(item()) : contents.get(0);
    }

    /**
     * One stack per bound block, in the order SC-170 §6 asks for.
     *
     * <p>Read from the ledger rather than from the pack list, because the ledger is what this world
     * actually bound — a pack installed and not enabled has no slot and belongs in no tab.
     */
    private static List<ItemStack> contents() {
        List<ItemStack> stacks = new ArrayList<>();
        for (BlockSlot slot : BoundBlocks.inMenuOrder()) {
            BoundBlocks.at(slot).ifPresent(bound -> stacks.add(AddonItem.of(
                    bound.logicalId(),
                    BoundBlocks.nameOf(bound.logicalId()),
                    Identifier.fromNamespaceAndPath(Lepus.MOD_ID,
                            BlockBinding.itemModelOf(slot)))));
        }
        // Items after blocks. Bedrock's own menu orders within a pack by category and puts
        // `items` last of the four, so this is the same answer at the scale we can currently give.
        for (BoundItems.Bound item : BoundItems.all()) {
            stacks.add(AddonItem.of(item.logicalId(), BoundBlocks.nameOf(item.logicalId()),
                    Identifier.fromNamespaceAndPath(Lepus.MOD_ID, item.modelPath()),
                    item.profile()));
        }
        return stacks;
    }
}
