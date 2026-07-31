package net.nennneko5787.sweetcookie.client.ui;

import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.nennneko5787.sweetcookie.core.ui.ViewCursor;
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
 * <p><b>Only that one method and its two type names differ.</b> Input is identical: both versions
 * take {@code keyPressed(KeyEvent)} with the same record behind it, both reach the server through
 * {@code getConnection().sendCommand(String)}, and both tick a screen the same way — checked against
 * the jars, not assumed. Everything above the drawing call is shared, which is the measure of
 * whether the abstraction in {@link ViewLayout} was drawn in the right place.
 */
public final class ViewScreen extends Screen {

    private final Screen parent;
    private final Supplier<ViewModel> source;
    private ViewModel view;
    private ViewCursor cursor;
    private int scroll;

    public ViewScreen(Screen parent, ViewModel view) {
        this(parent, () -> view);
    }

    /**
     * A screen that rebuilds its view.
     *
     * <p>Takes a supplier rather than a value because every action changes what the screen shows: a
     * pack that was just enabled belongs in the other section, and the row below it has different
     * keys. Rebuilding is cheap — it reads state already in memory — and it is the only way the
     * screen stays true after the command it sent takes effect.
     */
    public ViewScreen(Screen parent, Supplier<ViewModel> source) {
        super(Component.literal(source.get().title()));
        this.parent = parent;
        this.source = source;
        this.view = source.get();
        this.cursor = new ViewCursor(view);
    }

    /**
     * Rebuilds every client tick, not when an action is sent.
     *
     * <p>The command travels to the server and the answer comes back on another thread, so rebuilding
     * straight after sending would redraw the state the action was about to change — the screen would
     * show the pack still disabled and only correct itself on the next keypress. Twenty rebuilds a
     * second of an in-memory list costs nothing measurable and is right whenever the answer arrives.
     */
    @Override
    public void tick() {
        super.tick();
        int selected = cursor.selectedIndex();
        view = source.get();
        cursor = new ViewCursor(view).select(selected);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
            float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        ViewLayout.draw(view, (text, x, y, argb) ->
                extractor.text(this.font, text, x, y, argb), scroll, cursor.selectedIndex());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!cursor.handles(event.key())) {
            return super.keyPressed(event);
        }
        cursor.press(event.key()).ifPresent(this::run);
        scroll = clampScroll(ViewLayout.scrollTo(view, cursor.selectedIndex(), this.height, scroll));
        return true;
    }

    /**
     * Runs an action by sending its command.
     *
     * <p>SC-280 §7: the command is the operation and the screen calls it. Sending it as a command
     * costs no new packet, is refused by the same permission check as typing it, needs nothing of
     * the server beyond SweetCookie being installed there, and passes through ViaVersion untouched
     * because a chat command is vanilla traffic.
     */
    private void run(String command) {
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().sendCommand(command);
        }
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
