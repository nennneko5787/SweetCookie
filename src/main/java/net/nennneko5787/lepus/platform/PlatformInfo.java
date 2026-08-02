package net.nennneko5787.lepus.platform;

import java.nio.file.Path;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Which loader this is, which side, and where its directories are. SC-230 §3.
 *
 * <p><b>No loader types appear in any signature here</b> (SC-230 §2 rule 5). {@link Side} is ours
 * rather than Fabric's {@code EnvType} or NeoForge's {@code Dist}, because the moment a loader type
 * crosses this boundary, version-free code has to import it and the boundary has stopped being one.
 */
@SpecImpl("SC-230")
public interface PlatformInfo {

    /** Which physical side this process is. */
    enum Side {
        CLIENT,
        DEDICATED_SERVER
    }

    /** {@code fabric} or {@code neoforge}. For diagnostics and for the add-on screen's footer. */
    String loaderName();

    /** The loader's own version, as it reports it. */
    String loaderVersion();

    Side side();

    /** True in a development environment, where extra validation is worth its cost. */
    boolean isDevelopment();

    /** The game directory: {@code .minecraft}, or a server's working directory. */
    Path gameDirectory();

    /** {@code <game>/config}. */
    Path configDirectory();

    /**
     * Where Lepus keeps everything: {@code <game>/lepus}.
     *
     * <p>Installed add-ons live in one folder per kind underneath it, the way Bedrock itself splits
     * {@code behavior_packs} from {@code resource_packs} — see {@code PackKind}. Per instance, not
     * per world: packs are installed once and activated per world (SC-120 §8), which is as close to
     * Bedrock's model as Java allows.
     */
    default Path addonRoot() {
        return gameDirectory().resolve("lepus");
    }

    /**
     * The folder add-ons used to live in, before they were split by kind.
     *
     * <p>Kept only so that a user who already has files there is told where they went, rather than
     * finding their packs silently unloaded. Nothing scans it.
     */
    default Path legacyAddonDirectory() {
        return addonRoot().resolve("addons");
    }

    /** True on a physical client. Client-only services must not be requested otherwise (SCE-6003). */
    default boolean isClient() {
        return side() == Side.CLIENT;
    }
}
