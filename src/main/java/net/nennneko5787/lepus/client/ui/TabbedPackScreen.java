package net.nennneko5787.lepus.client.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import net.nennneko5787.lepus.Lepus;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.runtime.addon.PackKind;

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

    /** Wide enough for "Reload packs" without the label being clipped at any GUI scale. */
    private static final int RELOAD_WIDTH = 90;

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
                // Read this tab's selection out before the other one replaces the screen, or it is
                // simply gone and the box un-ticks itself on the way back. Vanilla's own commit is
                // the ONLY way to get it out of PackSelectionScreen - the model is private, and the
                // repository does not hold the selection until the model writes it there.
                //
                // SUPER's onClose, not this class's. Vanilla's is the commit plus closing the file
                // watcher and nothing else; the override below adds Screens.show(parent) after it.
                // A tab press that went through the override therefore closed the screen to a null
                // parent - which is to say back to the game - and then opened the other tab. Going
                // back to the game GRABS the mouse: Minecraft hides the cursor and puts it in the
                // middle of the window, and opening the next screen released it again, leaving it
                // where the grab had just put it. That round trip is the whole of the cursor jump.
                //
                // Flagged, because the commit is also what a real close uses to send commands. A
                // tab press must not send: doing so was a round trip and a client resource reload
                // per press, which flickered the window.
                AddonPackScreen.switching(true);
                try {
                    super.onClose();
                } finally {
                    AddonPackScreen.switching(false);
                }
                switcher.accept(selected);
            }
        });
        makeRoomFor(bar);
        addRenderableWidget(bar);

        // Top right, in the empty corner the tab bar leaves. Rescanning is a whole pass over the
        // add-on folders, so it is a button rather than something that happens on every tick or
        // every time the screen opens - a user who just edited a JSON by hand presses it, and a
        // user who did not never pays for it.
        addRenderableWidget(Button.builder(Component.literal("Reload packs"), button -> reload())
                .bounds(this.width - RELOAD_WIDTH - 4, 3, RELOAD_WIDTH, 20)
                .build());
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
            // Only the tall things give up height. The search box and the header labels are one
            // line each; shrinking them re-centres their text and was what put the search box on
            // top of the drag-and-drop hint.
            if (widget.getHeight() > BAR_HEIGHT * 2) {
                widget.setHeight(widget.getHeight() - BAR_HEIGHT);
            }
            if (widget instanceof AbstractSelectionList<?> list) {
                // A list caches where its contents sit against its own box, and setY/setHeight do
                // not invalidate that - which is why the first frame drew the rows against the old
                // geometry and one scroll notch fixed it. Re-setting the scroll amount is the public
                // way to make it recompute, and 0 is where a freshly opened list already was.
                list.setScrollAmount(0);
            }
        }
        PackTabBar.place(bar, this.width);
    }

    /**
     * Accepts a dropped {@code .mcaddon} or {@code .mcpack}.
     *
     * <p>Vanilla's own handler validates a dropped file as a <b>Java</b> pack and refuses anything
     * without a {@code pack.mcmeta}, so every Bedrock add-on was answered with "not a valid pack".
     * Overridden rather than worked around: the drop target is the tab's own folder, so a file
     * dropped on the behaviour tab lands in {@code be_behavior_pack}.
     *
     * <p>Copied, then rescanned, then the screen is rebuilt — a pack that has just been installed
     * has to appear without a restart, which is the loop SC-280 §1 exists for.
     *
     * <p>Anything that is not an archive or a folder is skipped rather than copied, and SAID so
     * through vanilla "not a pack" alert: the add-on folder is a place a user looks, and filling it
     * with whatever was dragged over the window makes it a worse place to look.
     */
    @Override
    public void onFilesDrop(List<Path> files) {
        List<Path> accepted = files.stream().filter(TabbedPackScreen::looksLikeAnAddon).toList();
        List<Path> rejected = files.stream().filter(file -> !looksLikeAnAddon(file)).toList();

        if (accepted.isEmpty()) {
            showRejected(rejected, () -> Screens.show(this));
            return;
        }
        // Vanilla's own dialog, down to the translation keys, so it is the prompt a user has already
        // seen when adding a resource pack and it is already in their language. Asking at all is the
        // point: a drop copies files into the game directory, and doing that without a word is a
        // surprise even when it is what the user meant.
        Screens.show(new ConfirmScreen(
                confirmed -> {
                    if (!confirmed) {
                        Screens.show(this);
                        return;
                    }
                    install(accepted);
                    // Rejected entries are reported AFTER the install, not instead of it: dropping
                    // four packs and a stray text file should install the four and then say which
                    // one was skipped.
                    showRejected(rejected, this::reload);
                },
                Component.translatable("pack.dropConfirm"),
                Component.literal(accepted.stream()
                        .map(file -> file.getFileName().toString())
                        .collect(Collectors.joining("\n")))));
    }

    /** Shows vanilla's "not a pack" alert, or runs {@code next} straight away when there is none. */
    private void showRejected(List<Path> rejected, Runnable next) {
        if (rejected.isEmpty()) {
            next.run();
            return;
        }
        Screens.show(new AlertScreen(
                next,
                Component.translatable("pack.dropRejected.title"),
                Component.translatable("pack.dropRejected.message", rejected.stream()
                        .map(file -> file.getFileName().toString())
                        .collect(Collectors.joining(", ")))));
    }

    private void install(List<Path> accepted) {
        Path target = kind.directoryIn(Lepus.platform().addonRoot());
        for (Path file : accepted) {
            try {
                Files.createDirectories(target);
                copyInto(file, target.resolve(file.getFileName().toString()));
            } catch (IOException failed) {
                // Never fatal: the screen stays open and the pack simply does not appear, which is
                // the same outcome as never dropping it. Constitution rule 5.
                System.out.println("[Lepus] could not install " + file + ": " + failed);
            }
        }
    }

    /**
     * Copies a file, or a whole folder.
     *
     * <p>Folders because an unpacked add-on is one — a {@code manifest.json} beside the content, no
     * archive anywhere. The loader already reads those (it opens a directory exactly as it opens a
     * zip), so refusing to install one would have been this screen inventing a restriction the rest
     * of the mod does not have.
     */
    private static void copyInto(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        try (Stream<Path> tree = Files.walk(source)) {
            for (Path entry : tree.toList()) {
                Path destination = target.resolve(source.relativize(entry).toString());
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(entry, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Reads the add-on folders again and rebuilds the screen.
     *
     * <p>Reopening rather than refreshing in place: the repository was built from the previous scan
     * and every row in both columns came from it, so there is nothing to update — only to replace.
     */
    private void reload() {
        Lepus.rescanAddons();
        switcher.accept(kind);
    }

    /** Bedrock ships add-ons as zip archives under two extensions, or as an unpacked folder. */
    private static boolean looksLikeAnAddon(Path file) {
        if (Files.isDirectory(file)) {
            return true;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mcaddon") || name.endsWith(".mcpack") || name.endsWith(".zip");
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
        // Vanilla's onClose commits the selection and closes its file watcher, and leaves the
        // screen up - every vanilla caller decides for itself what to show next. So the screen
        // change is this override's, which is why the tab switch above calls super and not this:
        // there is no way to ask for the commit alone once the two are stuck together here.
        super.onClose();
        Screens.show(parent);
    }
}
