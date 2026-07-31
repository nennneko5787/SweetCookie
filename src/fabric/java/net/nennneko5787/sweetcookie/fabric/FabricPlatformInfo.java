package net.nennneko5787.sweetcookie.fabric;

import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.nennneko5787.sweetcookie.platform.PlatformInfo;

/**
 * Fabric's {@link PlatformInfo}. SC-230 §3.
 *
 * <p>Compiled only by the Fabric nodes. Everything it touches is Fabric API; nothing it returns is,
 * which is the whole point of the interface being version-free and loader-free.
 */
public final class FabricPlatformInfo implements PlatformInfo {

    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public String loaderVersion() {
        return FabricLoader.getInstance().getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public Side side() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
                ? Side.CLIENT
                : Side.DEDICATED_SERVER;
    }

    @Override
    public boolean isDevelopment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Path gameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
