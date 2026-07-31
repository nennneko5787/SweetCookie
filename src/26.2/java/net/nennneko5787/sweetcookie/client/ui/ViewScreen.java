package net.nennneko5787.sweetcookie.client.ui;

import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.nennneko5787.sweetcookie.core.ui.ViewLayout;
import net.nennneko5787.sweetcookie.core.ui.ViewModel;

/**
 * A screen showing a {@link ViewModel}, for 26.2. SC-280 §3.1.
 *
 * <p>The same class as the one in {@code src/1.21.11/java}, against a different rendering model.
 * 26.2 replaced {@code Screen.render(GuiGraphics, …)} with
 * {@code Screen.extractRenderState(GuiGraphicsExtractor, …)} — the same submission-based rewrite
 * ADR-0010 found in the block and entity paths, applied to the UI — and moved text drawing from
 * {@code GuiGraphics.drawString} to {@code GuiGraphicsExtractor.text}.
 *
 * <p>Read-only. Pack selection is Minecraft own PackSelectionScreen (SC-280 section 5.2); this shows
 * the pool and the ledger, and the list a client sees when it is not the process running the world.
 * Everything above the drawing call is shared, which is the measure of whether the abstraction in
 * {@link ViewLayout} was drawn in the right place.
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
