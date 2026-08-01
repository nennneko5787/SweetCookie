package net.nennneko5787.sweetcookie.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.nennneko5787.sweetcookie.runtime.addon.PackKind;

/**
 * The behaviour/resource tab bar, for 1.21.11. SC-280 §5.2, SC-220 §3.
 *
 * <p>The only version-divergent part of the whole selection path. 1.21.11 builds a bar with
 * {@code TabNavigationBar.builder(TabManager, int)} and sizes it with {@code setWidth} plus a
 * no-argument {@code arrangeElements}; 26.2 renamed the two-argument entry point to
 * {@code MenuTabBar.builder} and takes the width in {@code arrangeElements(int)}. The counterpart in
 * {@code src/26.2/java} has the same class name and the same two methods.
 *
 * <p>The bar sits at the top and is not positioned: on 1.21.11 it is not a widget at all - it is an
 * {@code AbstractContainerEventHandler} with no setY - so the top is the only place it can be. The
 * screen makes room by moving its own content down instead, which works the same on both.
 *
 * <p>{@link GridLayoutTab} is vanilla's own empty {@code Tab} and exists on both versions, which is
 * what keeps that divergence out of here: 26.2 added {@code Tab.getLayout()}, and implementing
 * {@code Tab} by hand would have meant two implementations of it as well.
 *
 * <p>The tabs hold no widgets. Each one <b>is</b> a screen (see {@code TabbedPackScreen}), so
 * selecting one swaps the screen rather than swapping a panel's contents.
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
        TabNavigationBar bar = TabNavigationBar.builder(tabs, width)
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
        bar.setWidth(width);
        bar.arrangeElements();
    }
}
