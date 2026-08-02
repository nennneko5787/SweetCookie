package net.nennneko5787.lepus;

import net.nennneko5787.lepus.core.registry.SlotPool;
import net.nennneko5787.lepus.platform.LifecycleHooks;
import net.nennneko5787.lepus.platform.PlatformInfo;
import net.nennneko5787.lepus.platform.Services;
import net.nennneko5787.lepus.runtime.addon.AddonRegistry;
import net.nennneko5787.lepus.runtime.addon.PackKind;
import net.nennneko5787.lepus.runtime.addon.WorldActivation;
import net.nennneko5787.lepus.runtime.config.LepusConfig;
import net.nennneko5787.lepus.runtime.registry.BlockBinding;
import net.nennneko5787.lepus.runtime.registry.BlockPool;
import net.nennneko5787.lepus.platform.CommandRegistrar;
import net.nennneko5787.lepus.runtime.command.LepusCommand;
import net.nennneko5787.lepus.runtime.registry.WorldLedger;
import net.nennneko5787.lepus.core.ui.TextView;
import net.nennneko5787.lepus.runtime.ui.Views;

/**
 * Shared entry point. Version- and loader-independent.
 *
 * <p>This file exists in the shared source tree, so it is compiled by every
 * (Minecraft version x loader) node. Loader-specific code lives in {@code src/fabric/java} or
 * {@code src/neoforge/java}, added to the source set by that node's buildscript — not behind
 * {@code //?} comments, which are for divergences of five lines or fewer (SC-220 section 3).
 */
public final class Lepus {

    public static final String MOD_ID = "lepus";

    private static PlatformInfo platform;
    private static LifecycleHooks lifecycle;
    private static LepusConfig config;
    private static BlockPool blockPool;
    private static AddonRegistry addons = AddonRegistry.empty();

    private Lepus() {
    }

    /**
     * Called by each loader's entry point, early enough that the block registry is still open.
     *
     * <p>Everything registered here is anonymous. Not one Bedrock feature gets a registry entry
     * (constitution rule 7, ADR-0007), so add-ons attach and detach per world afterwards without
     * touching a registry again.
     */
    public static void init() {
        // Resolved once, eagerly, into fields (SC-230 §2 rule 3). A missing provider fails here
        // rather than at world load, which is the far worse place to discover one.
        platform = Services.load(PlatformInfo.class);
        lifecycle = Services.load(LifecycleHooks.class);
        Services.load(CommandRegistrar.class).onRegisterCommands(LepusCommand::register);

        config = LepusConfig.load(platform.configDirectory());

        // The pool is exactly what the config says. It is NOT grown to fit the saved worlds:
        // registration finishes here, before any world exists, so a pool sized from one world
        // would charge every other world in the instance for it - permanently, in BlockState
        // allocations and in palette width - and a world copied in later would still not fit.
        // A world whose ledger needs more reports SCE-4013 when it loads, naming the config line.
        SlotPool effective = config.pool();

        blockPool = BlockPool.register(effective);
        // Before the client loads resources on its way to the main menu, which is long before any
        // world and therefore before anything is bound. Without this every slot is missing a
        // blockstate at startup and the log fills with one line per state.
        BlockBinding.publishResources();
        WorldLedger.install(lifecycle, effective);
        WorldActivation.install(lifecycle);

        System.out.println("[Lepus] " + platform.loaderName() + " "
                + platform.loaderVersion() + " (" + platform.side() + "): registered "
                + blockPool.size() + " pool blocks, "
                + effective.totalStates() + " block states");

        // Installed, not activated (SC-120 §8). Scanned at server start rather than here because
        // parsing an add-on folder is real work and mod init is on the path to the main menu; a
        // dedicated server reaches its first server-start immediately anyway.
        lifecycle.onServerStarting(scope -> {
            rescanAddons();
            // After the scan and after the ledger and the activation set have loaded: binding needs
            // all three, and the hooks run in the order they were installed.
            BlockBinding.bindEnabled();
            TextView.render(Views.packs(addons, WorldActivation.known()))
                    .forEach(line -> System.out.println("[Lepus] " + line));
        });
    }

    /**
     * Reads the add-on folders again.
     *
     * <p>Called at server start and after the selection screen copies a dropped file in, because a
     * pack that has just been installed has to appear without a restart - that is the whole loop
     * SC-280 section 1 is scheduled around.
     */
    public static void rescanAddons() {
        addons = AddonRegistry.scan(PackKind.directoriesIn(platform.addonRoot()));
    }

    /**
     * The installed add-ons.
     *
     * <p>Empty until a server has started. Scanning is what SC-280's management screen lists and
     * what {@code /lepus packs} will report.
     */
    public static AddonRegistry addons() {
        return addons;
    }

    /** The loaded configuration. */
    public static LepusConfig config() {
        if (config == null) {
            throw new IllegalStateException("the config is loaded during mod init");
        }
        return config;
    }

    /** The resolved platform service. */
    public static PlatformInfo platform() {
        if (platform == null) {
            throw new IllegalStateException("platform services are resolved during mod init");
        }
        return platform;
    }

    /**
     * The registered pool.
     *
     * @throws IllegalStateException before {@link #init} has run, because a caller that reached the
     *     pool early would silently get nothing rather than the blocks it expected
     */
    public static BlockPool blockPool() {
        if (blockPool == null) {
            throw new IllegalStateException("the block pool is registered during mod init");
        }
        return blockPool;
    }
}
