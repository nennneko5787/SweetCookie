package net.nennneko5787.sweetcookie;

import net.nennneko5787.sweetcookie.core.registry.SlotPool;
import net.nennneko5787.sweetcookie.platform.PlatformInfo;
import net.nennneko5787.sweetcookie.platform.Services;
import net.nennneko5787.sweetcookie.runtime.registry.BlockPool;

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
    private static BlockPool blockPool;

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
        // Resolved once, eagerly, into a field (SC-230 §2 rule 3). A missing provider fails here
        // rather than at world load, which is the far worse place to discover one.
        platform = Services.load(PlatformInfo.class);

        // TODO(SC-120 §6.2): the effective pool is the element-wise maximum of the configured
        // default, every world's ledger and the installed packs. Config and ledger loading need
        // LifecycleHooks, which is not written yet, so this registers the default.
        // SlotPool.grownTo is the operation that will do it, and it exists and is tested.
        blockPool = BlockPool.register(SlotPool.DEFAULT);

        System.out.println("[SweetCookie] " + platform.loaderName() + " "
                + platform.loaderVersion() + " (" + platform.side() + "): registered "
                + blockPool.size() + " pool blocks, "
                + blockPool.pool().totalStates() + " block states");
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
