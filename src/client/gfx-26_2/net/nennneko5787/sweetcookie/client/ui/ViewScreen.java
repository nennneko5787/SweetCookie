package net.nennneko5787.sweetcookie.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.nennneko5787.sweetcookie.runtime.ui.ViewModel;

/**
 * A screen showing a {@link ViewModel}, for 26.2. SC-280 §3.1.
 *
 * <p>The same class as the one in {@code gfx-1_21_11}, against a different rendering model. 26.2
 * replaced {@code Screen.render(GuiGraphics, …)} with
 * {@code Screen.extractRenderState(GuiGraphicsExtractor, …)} — the same submission-based rewrite
 * ADR-0010 found in the block and entity paths, applied to the UI — and moved text drawing from
 * {@code GuiGraphics.drawString} to {@code GuiGraphicsExtractor.text}.
 *
 * <p>Two methods and two type names differ. Everything above them is shared, which is the measure of
 * whether the abstraction in {@link ViewLayout} was drawn in the right place.
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
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
            float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        ViewLayout.draw(view, (text, x, y, argb) ->
                extractor.text(this.font, text, x, y, argb), scroll);
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
