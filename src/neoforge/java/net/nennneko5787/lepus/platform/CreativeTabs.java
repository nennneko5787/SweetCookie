package net.nennneko5787.lepus.platform;

import net.minecraft.world.item.CreativeModeTab;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Starting a creative tab. The NeoForge spelling. SC-170 §6, SC-230 §3.
 *
 * <p><b>NeoForge places a mod's tabs itself</b>, and adds a no-argument builder to say so. It also
 * deprecates the positional one that vanilla exposes — which is the only clue the compiler gives,
 * and the clue was there before anyone read it: the deprecation warning appeared on the NeoForge
 * nodes alone for months.
 *
 * <p>Asking for a position is not merely discouraged here, it does not work. {@code Row.TOP} column
 * zero is where a vanilla tab already sits, so the tab was registered, built, filled — and never
 * seen. Every item in it was reachable only by giving it to yourself, which is exactly how it was
 * found.
 */
@SpecImpl("SC-170")
public final class CreativeTabs {

    private CreativeTabs() {
    }

    public static CreativeModeTab.Builder builder() {
        return CreativeModeTab.builder();
    }
}
