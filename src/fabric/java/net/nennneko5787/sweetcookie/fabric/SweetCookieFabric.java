package net.nennneko5787.sweetcookie.fabric;

import net.fabricmc.api.ModInitializer;

import net.nennneko5787.sweetcookie.SweetCookie;

/**
 * Fabric entry point.
 *
 * <p>Lives in {@code src/fabric/java}, which only the Fabric nodes compile. Loader-specific code
 * gets its own directory rather than a {@code //?} comment, because {@code //?} is for divergences
 * of five lines or fewer (constitution rule 12, SC-220 section 3).
 *
 * <p>This class does as little as possible: it exists to satisfy Fabric's entry-point contract and
 * hand control to shared code. Everything a loader can do differently is reached through a platform
 * service (SC-230), not through code duplicated here and in the NeoForge entry point.
 */
public final class SweetCookieFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        SweetCookie.init("fabric");
    }
}
