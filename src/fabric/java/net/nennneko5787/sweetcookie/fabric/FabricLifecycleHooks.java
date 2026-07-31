package net.nennneko5787.sweetcookie.fabric;

import java.nio.file.Path;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.nennneko5787.sweetcookie.platform.LifecycleHooks;

/**
 * Fabric's {@link LifecycleHooks}. SC-230 §3.
 *
 * <p>Only {@code ServerLifecycleEvents} is used, and that is a deliberate choice rather than a
 * coincidence: its members are <b>byte-identical</b> on the Fabric API builds for both supported
 * Minecraft versions (2.6.15 for 1.21.11, 4.1.3 for 26.2), verified with javap.
 *
 * <p>The per-<em>level</em> events are not identical. {@code ServerWorldEvents} on 1.21.11 is
 * {@code ServerLevelEvents} on 26.2 — same {@code LOAD} and {@code UNLOAD} fields, same nested
 * callback interfaces, different class name. That is a real version divergence and the first one
 * this project has hit in a loader API. Nothing needs those events yet, so nothing here pays for
 * them; when something does, the rename is small enough for a Stonecutter {@code //?} under
 * SC-220 §3's five-line rule, and this comment is the record that it was measured rather than
 * guessed.
 */
public final class FabricLifecycleHooks implements LifecycleHooks {

    @Override
    public void onServerStarting(Consumer<ServerScope> callback) {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> callback.accept(scopeOf(server)));
    }

    @Override
    public void onServerStopping(Consumer<ServerScope> callback) {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> callback.accept(scopeOf(server)));
    }

    static ServerScope scopeOf(MinecraftServer server) {
        return new ServerScope() {
            @Override
            public Path worldDataDirectory() {
                return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("sweetcookie");
            }

            @Override
            public boolean isSinglePlayer() {
                return server.isSingleplayer();
            }
        };
    }
}
