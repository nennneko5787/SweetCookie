package net.nennneko5787.sweetcookie;

import net.nennneko5787.sweetcookie.core.registry.SlotPool;
import net.nennneko5787.sweetcookie.platform.LifecycleHooks;
import net.nennneko5787.sweetcookie.platform.PlatformInfo;
import net.nennneko5787.sweetcookie.platform.Services;
import net.nennneko5787.sweetcookie.runtime.addon.AddonRegistry;
import net.nennneko5787.sweetcookie.runtime.config.SweetCookieConfig;
import net.nennneko5787.sweetcookie.runtime.registry.BlockPool;
import net.nennneko5787.sweetcookie.runtime.registry.WorldLedger;

/**
 * Shared entry point. Version- and loader-independent.
 *
 * <p>This file exists in the shared source tree, so it is compiled by every
 * (Minecraft version x loader) node. Loader-specific code lives in {@code src/fabric/java} or
 * {@code src/neoforge/java}, added to the source set by that node's buildscript — not behind
 * {@code //?} comments, which are for divergences of five lines or fewer (SC-220 section 3).
 */
public final class SweetCookie {

    public static final String MOD_ID = "sweetcookie";

    private static PlatformInfo platform;
    private static LifecycleHooks lifecycle;
    private static SweetCookieConfig config;
    private static BlockPool blockPool;
    private static AddonRegistry addons = AddonRegistry.empty();

    private SweetCookie() {
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

        config = SweetCookieConfig.load(platform.configDirectory());

        // The pool is exactly what the config says. It is NOT grown to fit the saved worlds:
        // registration finishes here, before any world exists, so a pool sized from one world
        // would charge every other world in the instance for it - permanently, in BlockState
        // allocations and in palette width - and a world copied in later would still not fit.
        // A world whose ledger needs more reports SCE-4013 when it loads, naming the config line.
        SlotPool effective = config.pool();

        blockPool = BlockPool.register(effective);
        WorldLedger.install(lifecycle, effective);

        System.out.println("[SweetCookie] " + platform.loaderName() + " "
                + platform.loaderVersion() + " (" + platform.side() + "): registered "
                + blockPool.size() + " pool blocks, "
                + effective.totalStates() + " block states");

        // Installed, not activated (SC-120 §8). Scanned at server start rather than here because
        // parsing an add-on folder is real work and mod init is on the path to the main menu; a
        // dedicated server reaches its first server-start immediately anyway.
        lifecycle.onServerStarting(scope -> {
            addons = AddonRegistry.scan(platform.addonDirectory());
            addons.describe().forEach(line -> System.out.println("[SweetCookie] " + line));
        });
    }

    /**
     * The installed add-ons.
     *
     * <p>Empty until a server has started. Scanning is what SC-280's management screen lists and
     * what {@code /sweetcookie packs} will report.
     */
    public static AddonRegistry addons() {
        return addons;
    }

    /** The loaded configuration. */
    public static SweetCookieConfig config() {
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
