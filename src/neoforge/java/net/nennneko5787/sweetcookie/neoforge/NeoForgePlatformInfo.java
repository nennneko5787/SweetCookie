package net.nennneko5787.sweetcookie.neoforge;

import java.nio.file.Path;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.nennneko5787.sweetcookie.platform.PlatformInfo;

/**
 * NeoForge's {@link PlatformInfo}. SC-230 §3.
 *
 * <p>Compiled only by the NeoForge nodes. The counterpart to {@code FabricPlatformInfo}: same
 * questions, entirely different APIs, and not one of them visible to the code that asks.
 */
public final class NeoForgePlatformInfo implements PlatformInfo {

    @Override
    public String loaderName() {
        return "neoforge";
    }

    @Override
    public String loaderVersion() {
        return ModList.get().getModContainerById("neoforge")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    // FMLEnvironment exposes these as METHODS, not as the public fields older NeoForge had.
    // Verified with javap against both loader jars (10.0.36 and 11.0.13); identical on each, so
    // there is no version divergence to hide behind a source directory.

    @Override
    public Side side() {
        return FMLEnvironment.getDist().isClient() ? Side.CLIENT : Side.DEDICATED_SERVER;
    }

    @Override
    public boolean isDevelopment() {
        return !FMLEnvironment.isProduction();
    }

    @Override
    public Path gameDirectory() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}
