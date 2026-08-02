package net.nennneko5787.lepus.client.ui;

import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.nennneko5787.lepus.core.ui.ViewLayout;
import net.nennneko5787.lepus.core.ui.ViewModel;

/**
 * A screen showing a {@link ViewModel}, for 1.21.11. SC-280 §3.1.
 *
 * <p>Lives in {@code src/1.21.11/java} because 26.2 has no {@code Screen.render(GuiGraphics, …)} to
 * override and no {@code GuiGraphics.drawString} to call. The counterpart in {@code src/26.2/java}
 * is the same class with the same name and a different body — nothing else in the project references
 * either by anything but that name, which is what SC-220 §3's directory split buys.
 *
 * <p>Everything above the one drawing call is shared: {@link ViewLayout} decides what lines exist,
 * where they go and what colour they are, and {@link ViewCursor} decides what the keys do. Both need
 * no Minecraft at all, so the screen's behaviour is unit-testable and only its pixels are not.
 */
public final class ViewScreen extends Screen {

    private final Screen parent;
    private final Supplier<ViewModel> source;
    private ViewModel view;
    private int scroll;

    public ViewScreen(Screen parent, ViewModel view) {
        this(parent, () -> view);
    }

    /**
     * A screen that rebuilds its view.
     *
     * <p>Takes a supplier rather than a value because what it shows changes underneath it: a pack
     * enabled from the selection screen, or a block bound into the ledger. Rebuilding reads state
     * already in memory, so it costs nothing worth measuring.
     */
    public ViewScreen(Screen parent, Supplier<ViewModel> source) {
        super(Component.literal(source.get().title()));
        this.parent = parent;
        this.source = source;
        this.view = source.get();
    }

    /**
     * Rebuilds every client tick.
     *
     * <p>State changes on the server thread and arrives whenever it arrives. Twenty rebuilds a second
     * of an in-memory list is right the moment it does, and costs nothing measurable.
     */
    @Override
    public void tick() {
        super.tick();
        view = source.get();
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
