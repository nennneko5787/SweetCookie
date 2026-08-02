package net.nennneko5787.lepus.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.nennneko5787.lepus.runtime.addon.PackKind;

/**
 * The behaviour/resource tab bar, for 26.2. SC-280 §5.2, SC-220 §3.
 *
 * <p>The only version-divergent part of the whole selection path, and it is small. 26.2 moved the
 * two-argument builder from {@code TabNavigationBar} to {@link MenuTabBar} — {@code TabNavigationBar}
 * itself now takes four coordinates — and folded {@code setWidth} into
 * {@code arrangeElements(int)}. {@code MenuTabBar} is a {@code TabNavigationBar}, so everything
 * above this file sees one type.
 *
 * <p>Two divergences do <b>not</b> appear here, because vanilla already absorbed them.
 * {@link GridLayoutTab} is a concrete public {@code Tab} on both versions, so 26.2 adding
 * {@code Tab.getLayout()} costs nothing; and {@code TabButton} became abstract here, which would
 * have mattered only if the tabs were added one at a time.
 *
 * <p>The bar sits at the top and is not positioned, matching 1.21.11, where it is not a widget and
 * cannot be. The screen makes room by moving its own content down.
 */
final class PackTabBar {

    private PackTabBar() {
    }

    static TabNavigationBar build(int width, PackKind current, Consumer<PackKind> onSelect) {
        Tab behavior = new GridLayoutTab(Component.literal(PackKind.BEHAVIOR.title()));
        Tab resource = new GridLayoutTab(Component.literal(PackKind.RESOURCE.title()));
        // The four-argument TabManager is the one with an onSelected hook, and it is the same on
        // both versions. The two widget consumers do nothing because these tabs own no widgets.
        TabManager tabs = new TabManager(widget -> {
        }, widget -> {
        }, tab -> onSelect.accept(tab == behavior ? PackKind.BEHAVIOR : PackKind.RESOURCE),
                tab -> {
                });
        TabNavigationBar bar = MenuTabBar.builder(tabs, width)
                .addTabs(behavior, resource)
                .build();
        // Selected without a sound: this bar is built during init, including the init of the screen
        // the user just arrived at, and announcing a click nobody made is wrong twice over.
        bar.selectTab(current == PackKind.BEHAVIOR ? 0 : 1, false);
        // SC-280 5.1 item Java Edition never states: which end of the selected column wins. It used
        // to be the screen title, which duplicated the tab and landed on vanilla drag-and-drop
        // hint. A tooltip is weaker than a heading and is the best place left once the tab names
        // the screen.
        Tooltip rule = Tooltip.create(
                Component.literal("The top of the selected list wins."));
        bar.setTabTooltip(0, rule);
        bar.setTabTooltip(1, rule);
        return bar;
    }

    static void place(TabNavigationBar bar, int width) {
        bar.arrangeElements(width);
    }
}
