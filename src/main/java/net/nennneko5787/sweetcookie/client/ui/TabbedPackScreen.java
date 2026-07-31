package net.nennneko5787.sweetcookie.client.ui;

import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.runtime.addon.PackKind;

/**
 * Minecraft's pack screen with a behaviour/resource tab bar above it. SC-280 §5.2.
 *
 * <p>A subclass rather than a replacement: {@code super.init()} builds the real two columns, the
 * real arrow buttons, the real search box and the real drag-and-drop, and this adds a bar above
 * them. Everything the previous revision of this file re-implemented is still vanilla's.
 *
 * <p><b>Each tab is a screen, not a panel.</b> Vanilla's {@code Tab} hosts widgets inside one
 * screen; it cannot host a {@code Screen}, and {@code PackSelectionScreen} is one. So selecting the
 * other tab opens the other screen — which draws the same bar in the same place with the other tab
 * lit, so it reads as a tab switch. It also means each tab has its own {@code PackRepository}, which
 * is what keeps a commit from one tab silent about the other kind.
 */
@SpecImpl("SC-280")
public final class TabbedPackScreen extends PackSelectionScreen {

    /**
     * How much vertical room the bar takes, in the units the rest of the screen is laid out in.
     *
     * <p>Vanilla's own tab bars are 24 high plus a little breathing room. Nothing exposes this as a
     * constant, so it is stated here and the shift below is measured against the real layout rather
     * than added to hard-coded coordinates.
     */
    private static final int BAR_HEIGHT = 30;

    private final Screen parent;
    private final PackKind kind;
    private final Consumer<PackKind> switcher;

    /**
     * @param switcher opens the screen for another kind — supplied rather than constructed here so
     *                 that this class does not need to know how a repository is built
     */
    public TabbedPackScreen(Screen parent, PackKind kind, PackRepository repository,
            Consumer<PackRepository> onCommit, Path packDir, Component title,
            Consumer<PackKind> switcher) {
        super(repository, onCommit, packDir, title);
        this.parent = parent;
        this.kind = kind;
        this.switcher = switcher;
    }

    @Override
    protected void init() {
        super.init();
        TabNavigationBar bar = PackTabBar.build(this.width, kind, selected -> {
            if (selected != kind) {
                switcher.accept(selected);
            }
        });
        makeRoomFor(bar);
        addRenderableWidget(bar);
    }

    /**
     * Moves vanilla's content down by the height of the bar.
     *
     * <p>This is the one place the mod reaches into a vanilla screen's layout, so it is done by
     * <b>measuring</b> rather than by assuming coordinates. The top of the content is whatever the
     * highest search box or list happens to be after {@code super.init()} has run; the bar goes
     * there, and those elements move down by its height. The lists also lose that height from their
     * own, so their bottoms — and the footer buttons, which are not touched — stay where vanilla put
     * them.
     *
     * <p>Measured rather than assumed because a hard-coded offset silently overlaps the moment
     * Mojang changes the header, and an overlapping list is still clickable: the failure would be a
     * tab bar that cannot be pressed rather than an exception.
     */
    private void makeRoomFor(TabNavigationBar bar) {
        int top = Integer.MAX_VALUE;
        for (GuiEventListener child : children()) {
            if (isContent(child)) {
                top = Math.min(top, ((AbstractWidget) child).getY());
            }
        }
        if (top == Integer.MAX_VALUE) {
            // Nothing to move, so nothing to make room for. Adding the bar anyway would put it on
            // top of whatever is there.
            return;
        }
        for (GuiEventListener child : children()) {
            if (!isContent(child)) {
                continue;
            }
            AbstractWidget widget = (AbstractWidget) child;
            widget.setY(widget.getY() + BAR_HEIGHT);
            if (!(widget instanceof EditBox)) {
                widget.setHeight(Math.max(BAR_HEIGHT, widget.getHeight() - BAR_HEIGHT));
            }
        }
        PackTabBar.place(bar, this.width);
    }

    /**
     * The header and the lists, but not the footer buttons.
     *
     * <p>By position rather than by type: the two lists and the search box are the tall things in
     * the upper half, and the buttons vanilla puts at the bottom must stay at the bottom. Testing
     * {@code getY} against the halfway point survives both lists being the same class as nothing
     * else on the screen, and does not name a private vanilla type.
     */
    private boolean isContent(GuiEventListener child) {
        return child instanceof AbstractWidget widget && widget.getY() < this.height / 2;
    }

    @Override
    public void onClose() {
        // Vanilla's onClose commits and then returns to ITS parent, which it took from Minecraft's
        // current screen. Ours is the screen the mod list came from, so it is restored here.
        super.onClose();
        this.minecraft.setScreenAndShow(parent);
    }
}
