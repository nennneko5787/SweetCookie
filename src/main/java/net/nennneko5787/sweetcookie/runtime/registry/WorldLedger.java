package net.nennneko5787.sweetcookie.runtime.registry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.registry.BlockLedger;
import net.nennneko5787.sweetcookie.core.registry.LedgerJson;
import net.nennneko5787.sweetcookie.core.registry.SlotPool;
import net.nennneko5787.sweetcookie.platform.LifecycleHooks;

/**
 * Holds the running world's ledger, and reads and writes it around a server's lifetime. SC-120 §6.3.
 *
 * <p>The Minecraft-side half of the ledger. All the logic — allocation, drift, remapping, the file
 * format — is in {@code core/registry} where it is tested in seconds; this is only the part that
 * needs to know when a world exists.
 */
@SpecImpl("SC-120")
public final class WorldLedger {

    private static BlockLedger current;

    private WorldLedger() {
    }

    /** Installs the load and save callbacks. Call once, from mod init, after services resolve. */
    public static void install(LifecycleHooks hooks, SlotPool pool) {
        hooks.onServerStarting(scope -> load(scope.worldDataDirectory(), pool));
        hooks.onServerStopping(scope -> save(scope.worldDataDirectory()));
    }

    /**
     * The running world's ledger.
     *
     * <p>Empty outside a server. A caller on a physical client between worlds has no ledger and
     * must not invent one: the client binds slots per session from the sideband and never persists
     * them (SC-120 §9).
     */
    public static Optional<BlockLedger> current() {
        return Optional.ofNullable(current);
    }

    private static void load(Path directory, SlotPool configured) {
        try {
            Optional<LedgerJson.Contents> saved = LedgerJson.read(directory);
            if (saved.isEmpty()) {
                current = new BlockLedger(configured);
                return;
            }
            // SC-120 §6.2: the effective pool is the element-wise maximum, so a world whose ledger
            // outgrew the configured default still loads. `blockPoolAutoGrow: false` is the case
            // that refuses instead with SCE-4013, and it is not wired yet.
            //
            // TODO(SC-120 §6.2): growing the pool here does not enlarge what was REGISTERED - the
            // registry froze during init, before any world existed. Until the pool is sized from
            // every world's ledger at startup, a world needing more than the default gets a ledger
            // it cannot fully bind. The exhaustion path reports it rather than corrupting anything.
            current = BlockLedger.restore(
                    configured.grownTo(saved.get().pool()), saved.get().bindings());
        } catch (IOException | RuntimeException unreadable) {
            // Never fatal. A world that will not start because its ledger is damaged is a worse
            // outcome than one that starts with its custom blocks unbound and says so; the file is
            // not overwritten, so a human can still recover it.
            System.out.println("[SweetCookie] SCE-4014 ledger unreadable at " + directory
                    + ": " + unreadable + " - starting with an empty ledger and NOT overwriting it");
            current = null;
        }
    }

    private static void save(Path directory) {
        BlockLedger ledger = current;
        current = null;
        if (ledger == null) {
            return;
        }
        try {
            LedgerJson.write(directory, ledger);
        } catch (IOException failed) {
            System.out.println("[SweetCookie] failed to write the ledger at " + directory
                    + ": " + failed);
        }
    }
}
