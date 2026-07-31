package net.nennneko5787.sweetcookie.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.nennneko5787.sweetcookie.runtime.ui.ViewModel;

/**
 * A screen showing a {@link ViewModel}, for 1.21.11. SC-280 §3.1.
 *
 * <p>Lives in {@code src/client/gfx-1_21_11} because 26.2 has no {@code Screen.render(GuiGraphics,
 * …)} to override and no {@code GuiGraphics.drawString} to call. The counterpart in
 * {@code gfx-26_2} is the same class with the same name and a different body — nothing else in the
 * project references either by anything but that name, which is what SC-220 §3's directory split
 * buys.
 *
 * <p>Everything above the one drawing call is shared: {@link ViewLayout} decides what lines exist,
 * where they go and what colour they are, and it needs no Minecraft at all.
 */
public final class ViewScreen extends Screen {

    private final Screen parent;
    private final ViewModel view;
    private int scroll;

    public ViewScreen(Screen parent, ViewModel view) {
        super(Component.literal(view.title()));
        this.parent = parent;
        this.view = view;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ViewLayout.draw(view, (text, x, y, argb) ->
                graphics.drawString(this.font, text, x, y, argb), scroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scroll = clampScroll(scroll - (int) (deltaY * 12));
        return true;
    }

    private int clampScroll(int candidate) {
        int overflow = Math.max(0, ViewLayout.height(view) - this.height + 16);
        return Math.max(0, Math.min(candidate, overflow));
    }

    @Override
    public void onClose() {
        // setScreenAndShow, not setScreen: 1.21.11 has both and 26.2 has only this one, so the
        // common spelling keeps onClose out of the version-divergent surface.
        this.minecraft.setScreenAndShow(parent);
    }
}
