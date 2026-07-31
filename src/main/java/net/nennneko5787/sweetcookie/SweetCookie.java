package net.nennneko5787.sweetcookie;

import net.nennneko5787.sweetcookie.core.registry.PoolSizing;
import net.nennneko5787.sweetcookie.core.registry.SlotPool;
import net.nennneko5787.sweetcookie.platform.LifecycleHooks;
import net.nennneko5787.sweetcookie.platform.PlatformInfo;
import net.nennneko5787.sweetcookie.platform.Services;
import net.nennneko5787.sweetcookie.runtime.config.SweetCookieConfig;
import net.nennneko5787.sweetcookie.runtime.registry.BlockPool;
import net.nennneko5787.sweetcookie.runtime.registry.LedgerScan;
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

    private SweetCookie() {
    }

    /**
     * Called by each loader's entry point, early enough that the block registry is still open.
     *
     * <p>Everything registered here is anonymous. Not one Bedrock feature gets a registry entry
     * (constitution rule 7, ADR-0007), so add-ons attach and detach per world afterwards without
     * touching a registry again.
     *
     * <p>The order is forced rather than chosen. The pool's <b>size</b> depends on what every world
     * in the instance already uses, and the registry freezes moments later — so the ledgers have to
     * be read before any world exists to ask (SC-120 §6.2), from files rather than from the game.
     */
    public static void init() {
        // Resolved once, eagerly, into fields (SC-230 §2 rule 3). A missing provider fails here
        // rather than at world load, which is the far worse place to discover one.
        platform = Services.load(PlatformInfo.class);
        lifecycle = Services.load(LifecycleHooks.class);

        config = SweetCookieConfig.load(platform.configDirectory());
        PoolSizing.Result sizing = PoolSizing.effective(
                config.pool(), LedgerScan.requirements(platform.gameDirectory()));

        SlotPool effective = switch (sizing) {
            case PoolSizing.Result.Register register -> {
                register.growth().forEach(growth -> System.out.println(
                        "[SweetCookie] block pool grown for an existing world: " + growth.advice()));
                yield register.pool();
            }
            case PoolSizing.Result.Refuse refuse -> {
                // SCE-4013. blockPoolAutoGrow is off, so an operator pinned the palette size and
                // wants to be told rather than accommodated. The game still starts: the worlds that
                // fit are unaffected, and the one that does not reports its own exhaustion when it
                // loads, naming the content it could not bind.
                refuse.shortfall().forEach(growth -> System.out.println(
                        "[SweetCookie] SCE-4013 a saved world needs more than the pinned block pool: "
                                + growth.advice()));
                yield config.pool().configured();
            }
        };

        blockPool = BlockPool.register(effective);
        WorldLedger.install(lifecycle, effective);

        System.out.println("[SweetCookie] " + platform.loaderName() + " "
                + platform.loaderVersion() + " (" + platform.side() + "): registered "
                + blockPool.size() + " pool blocks, "
                + effective.totalStates() + " block states");
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
