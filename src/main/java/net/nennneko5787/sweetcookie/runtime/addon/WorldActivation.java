package net.nennneko5787.sweetcookie.runtime.addon;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.registry.ActiveJson;
import net.nennneko5787.sweetcookie.core.registry.ActivePacks;
import net.nennneko5787.sweetcookie.platform.LifecycleHooks;

/**
 * Which packs the running world uses. SC-120 §8.
 *
 * <p>Read at server start and written <b>on every change</b> rather than at shutdown. A user who
 * enables a pack and then crashes should find it enabled, and the file is small enough that writing
 * it eagerly costs nothing worth measuring.
 */
@SpecImpl("SC-120")
public final class WorldActivation {

    private static ActivePacks current = ActivePacks.NONE;
    private static Path directory;

    private WorldActivation() {
    }

    /** Installs the load and save callbacks. Call once, from mod init, after services resolve. */
    public static void install(LifecycleHooks hooks) {
        hooks.onServerStarting(scope -> load(scope.worldDataDirectory()));
        hooks.onServerStopping(scope -> {
            current = ActivePacks.NONE;
            directory = null;
        });
    }

    /** What this world has enabled, in precedence order. Empty outside a server. */
    public static ActivePacks current() {
        return current;
    }

    /**
     * The activation state, when this process is the one that holds it.
     *
     * <p>Empty on a client connected to a remote server, where the world's pack set lives on the
     * other end of the connection and this process has never seen it. That is a different thing from
     * "no packs enabled", and {@link #current} cannot tell them apart — a client asked for a list
     * would otherwise show every installed pack as disabled and be confidently wrong. Callers that
     * show something to a user want this one; SC-270 §9's handshake is what will eventually let a
     * remote client answer properly.
     */
    public static Optional<ActivePacks> known() {
        return directory == null ? Optional.empty() : Optional.of(current);
    }

    /**
     * Applies a change and persists it.
     *
     * <p>Takes a function rather than a new value so that a caller cannot compute its change against
     * a stale copy — the read and the write happen here, together.
     *
     * @return the new state, or empty when there is no world to change
     */
    public static Optional<ActivePacks> update(UnaryOperator<ActivePacks> change) {
        if (directory == null) {
            return Optional.empty();
        }
        ActivePacks updated = change.apply(current);
        current = updated;
        try {
            ActiveJson.write(directory, updated);
        } catch (IOException failed) {
            // The change stays applied in memory. Reverting would leave the user looking at a pack
            // list that did not do what they asked, with no explanation; an unwritable save
            // directory is a problem they need told about, not one to paper over by undoing.
            System.out.println("[SweetCookie] could not write " + ActiveJson.FILE_NAME
                    + " in " + directory + ": " + failed + " - the change applies to this session"
                    + " only");
        }
        return Optional.of(updated);
    }

    private static void load(Path worldData) {
        directory = worldData;
        try {
            current = ActiveJson.read(worldData).orElse(ActivePacks.NONE);
        } catch (IOException | RuntimeException unreadable) {
            current = ActivePacks.NONE;
            System.out.println("[SweetCookie] could not read " + ActiveJson.FILE_NAME
                    + " in " + worldData + ": " + unreadable + " - no packs enabled this session,"
                    + " and the file is left alone");
        }
    }
}
