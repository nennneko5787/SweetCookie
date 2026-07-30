package net.nennneko5787.sweetcookie;

import net.nennneko5787.sweetcookie.core.format.BedrockId;

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

    private SweetCookie() {
    }

    /**
     * Called by each loader's entry point once the platform services are available.
     */
    public static void init(String loaderName) {
        // Proves the composite build wiring: a Minecraft-dependent module can see core/, and
        // core/ cannot see Minecraft. ADR-0001.
        BedrockId probe = BedrockId.parse("sweetcookie:bootstrap");
        System.out.println("[SweetCookie] " + loaderName + " init, core reachable: " + probe);
    }
}
