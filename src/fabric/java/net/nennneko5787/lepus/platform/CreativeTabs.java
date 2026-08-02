package net.nennneko5787.lepus.platform;

import net.minecraft.world.item.CreativeModeTab;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Starting a creative tab. The Fabric spelling. SC-170 §6, SC-230 §3.
 *
 * <p>Vanilla has exactly one way to begin a tab — {@code builder(Row, int)} — and the position is
 * not optional. Top row, column zero: nothing repositions a tab on this loader, and the tab shows
 * up beside the vanilla ones.
 *
 * <p>See the NeoForge file for why this is split at all. One line each, and the alternative was a
 * tab that silently did not exist on one of the two loaders.
 */
@SpecImpl("SC-170")
public final class CreativeTabs {

    private CreativeTabs() {
    }

    @SuppressWarnings("deprecation") // Deprecated by NeoForge's patch, not by vanilla. See below.
    public static CreativeModeTab.Builder builder() {
        return CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0);
    }
}
