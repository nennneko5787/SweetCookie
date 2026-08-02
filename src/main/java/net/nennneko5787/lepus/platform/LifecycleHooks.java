package net.nennneko5787.lepus.platform;

import java.nio.file.Path;
import java.util.function.Consumer;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Server start and stop, abstracted over the two loaders' event systems. SC-230 §3.
 *
 * <p>The hook the block ledger hangs from: SC-120 §6.3 puts it at
 * {@code <world>/data/lepus/ledger.json}, so it must be read once the save directory exists
 * and written before the process leaves.
 *
 * <p>{@link ServerScope} rather than {@code MinecraftServer} in the signature — not because
 * {@code MinecraftServer} is a loader type (it is not) but because everything this interface's
 * callers need from a server is a path and a way to say "this is single player". Handing them the
 * server itself would invite the rest of the codebase to reach through the hook for whatever else it
 * happened to want, and the hook would stop being one.
 */
@SpecImpl("SC-230")
public interface LifecycleHooks {

    /** What a lifecycle callback is given. Deliberately narrow. */
    interface ServerScope {

        /**
         * The world's data directory, {@code <world>/data/lepus}, created if absent.
         *
         * <p>Per world, matching SC-120 §8: packs are installed per instance and activated per
         * world, which is as close to Bedrock's model as Java allows.
         */
        Path worldDataDirectory();

        /** True for an integrated (single-player) server. */
        boolean isSinglePlayer();
    }

    /**
     * Runs once per server, after the save directory exists and before the world is playable.
     *
     * <p>This is where the ledger is read and slots are bound. It is <b>not</b> where the pool is
     * registered: registration must have finished before this, because Java freezes its registries
     * long before a world is selected (SC-120 §1).
     */
    void onServerStarting(Consumer<ServerScope> callback);

    /**
     * Runs once per server, while the save directory is still writable.
     *
     * <p>Stopping rather than stopped: SC-120 §6.3 requires the ledger to be written atomically
     * after every change, and the last chance to write anything is before the save closes.
     */
    void onServerStopping(Consumer<ServerScope> callback);
}
