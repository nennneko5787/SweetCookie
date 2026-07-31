package net.nennneko5787.sweetcookie.neoforge;

import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.nennneko5787.sweetcookie.platform.LifecycleHooks;

/**
 * NeoForge's {@link LifecycleHooks}. SC-230 §3.
 *
 * <p>{@code NeoForge.EVENT_BUS.addListener} rather than {@code @SubscribeEvent}, because a listener
 * added by hand can close over the callback it was given; an annotated static method cannot, and
 * would need a static registry of callbacks to reach one — state in the implementation, which
 * SC-230 §2 rule 6 says belongs in shared code instead.
 *
 * <p>{@code ServerStartingEvent} and {@code ServerStoppingEvent} exist at the same names on both
 * NeoForge versions (21.11.45 and 26.2.0.40-beta), verified by listing both universal jars.
 */
public final class NeoForgeLifecycleHooks implements LifecycleHooks {

    @Override
    public void onServerStarting(Consumer<ServerScope> callback) {
        NeoForge.EVENT_BUS.addListener(ServerStartingEvent.class,
                event -> callback.accept(scopeOf(event.getServer())));
    }

    @Override
    public void onServerStopping(Consumer<ServerScope> callback) {
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class,
                event -> callback.accept(scopeOf(event.getServer())));
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
