package net.nennneko5787.lepus.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Putting a screen on screen. The 1.21.11 spelling. SC-280 §3.1.
 *
 * <p>Here the current screen still belongs to {@code Minecraft} and the plain setter is
 * {@code setScreen}. The 26.2 file is the same method under a different owner — see it for why the
 * plain setter is the one to use and not {@code setScreenAndShow}.
 */
@SpecImpl("SC-280")
public final class Screens {

    private Screens() {
    }

    /** Shows a screen, without forcing a frame. */
    public static void show(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }
}
