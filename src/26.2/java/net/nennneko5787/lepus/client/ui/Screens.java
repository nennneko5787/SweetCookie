package net.nennneko5787.lepus.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Putting a screen on screen. The 26.2 spelling. SC-280 §3.1.
 *
 * <p>26.2 moved the current screen from {@code Minecraft} onto {@code Gui}, so the plain setter
 * lives there now. What remains on {@code Minecraft} is {@code setScreenAndShow}, which is that
 * setter <b>plus a forced {@code renderFrame}</b> — it exists for the places that must show
 * something before the next tick, such as a loading screen.
 *
 * <p><b>A tab switch is not one of those places.</b> Forcing a frame in the middle of replacing a
 * screen draws one outside the normal loop, and that frame is what flashed black every time
 * somebody pressed a tab.
 */
@SpecImpl("SC-280")
public final class Screens {

    private Screens() {
    }

    /** Shows a screen, without forcing a frame. */
    public static void show(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }
}
