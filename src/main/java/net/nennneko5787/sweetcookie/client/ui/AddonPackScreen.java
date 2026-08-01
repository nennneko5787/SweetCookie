package net.nennneko5787.sweetcookie.client.ui;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.nennneko5787.sweetcookie.SweetCookie;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostic;
import net.nennneko5787.sweetcookie.core.format.diag.Severity;
import net.nennneko5787.sweetcookie.core.format.value.PackId;
import net.nennneko5787.sweetcookie.core.registry.ActivePacks;
import net.nennneko5787.sweetcookie.core.registry.ActivePlan;
import net.nennneko5787.sweetcookie.runtime.addon.PackKind;
import net.nennneko5787.sweetcookie.runtime.addon.PackSummary;
import net.nennneko5787.sweetcookie.runtime.addon.WorldActivation;
import net.nennneko5787.sweetcookie.runtime.ui.Views;

/**
 * The add-on selection screen. SC-280 §5.2.
 *
 * <p><b>Minecraft's own pack screen, with our packs in it.</b> Not a copy of it and not something
 * that resembles it: {@code PackSelectionScreen} is constructed directly, so selecting an add-on is
 * the same two columns, the same arrow buttons on hover, the same search box and the same drag
 * behaviour as selecting a resource pack — because it is that screen. Nothing about the interaction
 * can drift from Java Edition's, because there is no second implementation of it to drift.
 *
 * <p>An earlier revision built a bespoke list. That was the wrong call: a pack list that had to be
 * operated some other way is the surprising one, and every hour spent on the bespoke one was spent
 * re-deriving behaviour that shipped with the game.
 *
 * <p><b>This class is version-free.</b> Every type it touches was checked against both merged jars
 * and is identical, constructor signatures included — {@code PackSelectionScreen},
 * {@code PackRepository}, {@code Pack}, {@code PackLocationInfo}, {@code Pack.Metadata},
 * {@code PackSelectionConfig}, {@code PackResources}, {@code IoSupplier}. That is a strong result
 * given that 26.2 replaced the screen rendering model outright (SC-280 §3.1): the rewrite went
 * through {@code Screen}'s own drawing, and this screen never draws. The single exception in the
 * whole path is building the tab bar, which is why {@code PackTabBar} — and only that — has a copy
 * per version directory.
 *
 * <p>Two tabs, one per {@link PackKind}, because Bedrock separates behaviour packs from resource
 * packs everywhere it lists them and an {@code .mcaddon} normally unpacks into one of each. Each tab
 * is a whole screen with its own repository, which is what lets a commit from one say nothing about
 * the other.
 */
@SpecImpl("SC-280")
public final class AddonPackScreen {

    private AddonPackScreen() {
    }

    /**
     * The screen to open, which is not always the selection screen.
     *
     * <p>A client connected to a remote server has not been told what that world enabled (SC-280
     * §5.3). Opening a selection screen there would show every pack unselected, which is a confident
     * wrong answer, and committing it would enable packs against a list nobody had seen. So that
     * client gets the read-only view, which says the server decides.
     */
    public static Screen open(Screen parent) {
        return WorldActivation.known().isPresent()
                ? selection(parent, PackKind.BEHAVIOR)
                : new ViewScreen(parent,
                        () -> Views.packs(SweetCookie.addons(), WorldActivation.known()));
    }

    /**
     * One tab's screen.
     *
     * <p>Rebuilt whenever a tab is chosen rather than kept side by side, so that switching tabs
     * after committing shows what the commit did. Building one is reading two in-memory lists.
     */
    private static Screen selection(Screen parent, PackKind kind) {
        ActivePacks active = WorldActivation.current();
        return new TabbedPackScreen(
                parent,
                kind,
                repositoryOf(active, kind),
                committed -> apply(committed, kind),
                kind.directoryIn(SweetCookie.platform().addonRoot()),
                // EMPTY. The tab already names the screen, and a title here lands on top of
                // vanilla drag-and-drop hint once the tab bar has pushed the header down. SC-280
                // 5.1 still item - which end of the order wins - moved to the tab tooltip.
                Component.empty(),
                chosen -> Minecraft.getInstance().setScreenAndShow(selection(parent, chosen)));
    }

    /**
     * Builds a repository holding one tab's packs.
     *
     * <p>Freshly built each time the screen opens rather than kept: it is a view of an
     * {@link net.nennneko5787.sweetcookie.runtime.addon.AddonRegistry AddonRegistry} that is itself
     * rebuilt on every scan, and a repository that outlived one would list packs that are no longer
     * installed.
     *
     * <p>Filtered to the tab's kind, including the selection — handing the screen a selected pack it
     * has no row for would make it disappear on commit.
     */
    private static PackRepository repositoryOf(ActivePacks active, PackKind kind) {
        PackRepository repository = new PackRepository(consumer -> {
            for (PackSummary pack : packsOf(kind)) {
                consumer.accept(packOf(pack));
            }
        });
        repository.reload();
        // setSelected takes lowest priority first, which is the direction ActivePacks stores. Read
        // out of the jar rather than assumed: PackSelectionModel reverses on the way in and
        // Lists.reverse on commit, so the screen shows highest-first while the repository is
        // lowest-first. Getting this backwards would silently invert every user's overrides.
        Set<PackId> ofKind = idsOf(kind);
        repository.setSelected(active.order().stream()
                .filter(ofKind::contains)
                .map(PackId::toString)
                .toList());
        return repository;
    }

    private static List<PackSummary> packsOf(PackKind kind) {
        return SweetCookie.addons().packs().stream().filter(kind::includes).toList();
    }

    /** Every installed pack of a kind, so that "not selected" can be told from "not in this tab". */
    private static Set<PackId> idsOf(PackKind kind) {
        return packsOf(kind).stream().map(PackSummary::id).collect(Collectors.toSet());
    }

    /**
     * The one place a loader disagrees, and the reason the four-argument form is kept.
     *
     * <p>NeoForge adds a fifth component to {@code Pack.Metadata} and deprecates the canonical
     * constructor in favour of it. Fabric's jar has only the four-argument form, so calling the
     * five-argument one would mean splitting this class per loader for a single boolean that
     * defaults to what we want anyway. The deprecated call compiles on both and behaves identically
     * on both; the warning is suppressed here rather than everywhere, so a second deprecation would
     * still be seen.
     */
    @SuppressWarnings("deprecation")
    private static Pack packOf(PackSummary pack) {
        PackLocationInfo location = new PackLocationInfo(
                // The identity, not the name: two add-ons may share a display name, and the
                // repository keys on this.
                pack.id().toString(),
                Component.literal(pack.name().isEmpty() ? pack.source() : pack.name()),
                PackSource.DEFAULT,
                Optional.empty());
        Pack.Metadata metadata = new Pack.Metadata(
                describe(pack),
                // COMPATIBLE regardless of what is wrong with the pack. PackCompatibility's other
                // values render as "made for a newer/older version of Minecraft", which would be a
                // lie about a Bedrock pack that failed to parse. What is wrong is said in the
                // description instead, in words that are true.
                PackCompatibility.COMPATIBLE,
                FeatureFlagSet.of(),
                List.of());
        // Selecting a pack puts it at the TOP of the selected column, which is the winning end and
        // matches ActivePacks.enable appending to the highest-priority end. Not required, not fixed:
        // every add-on can be deselected and moved.
        PackSelectionConfig selection = new PackSelectionConfig(false, Pack.Position.TOP, false);
        return new Pack(location, new IconOnly(pack.icon()), metadata, selection);
    }

    /**
     * The two description lines the screen shows under a pack's name.
     *
     * <p>What it provides, because SC-280 §5 asks for it and a folder of {@code .mcaddon} files is
     * otherwise indistinguishable. Then its errors, in red — Java Edition's screen says nothing at
     * all when a pack fails, and "why is this pack doing nothing" is the question this answers.
     */
    private static Component describe(PackSummary pack) {
        String provides = pack.provides().describe();
        Component summary = Component.literal(pack.version()
                + (provides.isEmpty() ? " - provides nothing this build reads" : " - " + provides));
        List<String> errors = pack.diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity() == Severity.ERROR)
                .map(Diagnostic::toString)
                .toList();
        if (!errors.isEmpty()) {
            return summary.copy().append(Component.literal("\n" + errors.get(0)
                    + (errors.size() > 1 ? " (+" + (errors.size() - 1) + " more)" : ""))
                    .withStyle(ChatFormatting.RED));
        }
        long warnings = pack.count(Severity.WARNING);
        return warnings == 0
                ? summary
                : summary.copy().append(Component.literal("\n" + warnings + " warning(s)")
                        .withStyle(ChatFormatting.YELLOW));
    }

    /**
     * Turns what the user selected into commands. SC-280 §7.1.
     *
     * <p>Diffed rather than replayed: closing the screen without changing anything sends nothing,
     * and moving one pack sends one command. Replaying the whole list would put two lines of chat
     * per installed pack in front of a user who moved one of them.
     */
    private static void apply(PackRepository committed, PackKind kind) {
        List<PackId> selected = new ArrayList<>();
        for (String id : committed.getSelectedIds()) {
            PackId.parse(id).ifPresent(selected::add);
        }
        ActivePacks current = WorldActivation.current();
        // This tab decided about its own kind and said nothing about the other. Handing the
        // selection straight to between() would read every resource pack as deselected and disable
        // the lot; spliceKind replaces this kind's entries in place and leaves the rest alone.
        List<PackId> desired = ActivePlan.spliceKind(current.order(), selected, idsOf(kind));
        ActivePlan plan = ActivePlan.between(current, desired);
        for (ActivePlan.Step step : plan.steps()) {
            send(commandFor(step));
        }
    }

    private static String commandFor(ActivePlan.Step step) {
        return switch (step) {
            case ActivePlan.Disable disable -> "sweetcookie disable " + disable.pack();
            case ActivePlan.Enable enable -> "sweetcookie enable " + enable.pack();
            // Positions are 1-based for a user and 0-based inside.
            case ActivePlan.Order order ->
                    "sweetcookie order " + (order.position() + 1) + " " + order.pack();
        };
    }

    private static void send(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null) {
            client.getConnection().sendCommand(command);
        }
    }

    /**
     * A pack that serves its icon and nothing else.
     *
     * <p>{@code PackSelectionScreen} opens every pack to read {@code pack.png}, so a repository
     * needs real resources even when nothing will ever load content from it. Bedrock's icon is
     * {@code pack_icon.png}, read at scan time into {@link PackSummary#icon} and handed over under
     * the name Java Edition looks for.
     *
     * <p>Everything else answers "not here". A null {@link IoSupplier} is how vanilla's own
     * implementations report an absent file, so this is the normal shape of an empty pack rather
     * than a stub with holes in it.
     */
    private record IconOnly(Optional<byte[]> icon) implements Pack.ResourcesSupplier {

        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            return new Resources(location, icon);
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            return new Resources(location, icon);
        }

        private record Resources(PackLocationInfo location, Optional<byte[]> icon)
                implements PackResources {

            @Override
            public IoSupplier<InputStream> getRootResource(String... path) {
                if (icon.isEmpty() || path.length != 1 || !path[0].equals("pack.png")) {
                    return null;
                }
                return () -> new ByteArrayInputStream(icon.get());
            }

            @Override
            public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
                return null;
            }

            @Override
            public void listResources(PackType type, String namespace, String prefix,
                    ResourceOutput output) {
            }

            @Override
            public Set<String> getNamespaces(PackType type) {
                return Set.of();
            }

            @Override
            public <T> T getMetadataSection(MetadataSectionType<T> type) {
                return null;
            }

            @Override
            public void close() {
            }
        }
    }
}
