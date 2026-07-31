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

    private static void load(Path directory, SlotPool registered) {
        try {
            Optional<LedgerJson.Contents> saved = LedgerJson.read(directory);
            if (saved.isEmpty()) {
                current = new BlockLedger(registered);
                return;
            }

            // The ledger is restored against WHAT WAS REGISTERED, never against what it asks for.
            //
            // An earlier revision grew the pool here to fit the ledger, which was wrong in a way
            // worth recording: registration finished during init, before any world existed, so
            // growing the ledger's view of the pool only let it hand out slots that no registered
            // block backs. Not growing it is strictly better — the allocator reports SCE-4010 with
            // a class and a count instead of silently producing bindings that resolve to nothing.
            BlockLedger ledger = BlockLedger.restore(registered, saved.get().bindings());
            reportSlotsOutsideThePool(registered, ledger, directory);
            current = ledger;
        } catch (IOException | RuntimeException unreadable) {
            // Never fatal. A world that will not start because its ledger is damaged is a worse
            // outcome than one that starts with its custom blocks unbound and says so; the file is
            // not overwritten, so a human can still recover it.
            System.out.println("[SweetCookie] SCE-4014 ledger unreadable at " + directory
                    + ": " + unreadable + " - starting with an empty ledger and NOT overwriting it");
            current = null;
        }
    }

    /**
     * Reports bindings this build cannot back with a registered block. {@code SCE-4013}.
     *
     * <p>Happens when a world was saved by an instance whose {@code blockPool} was larger — a world
     * copied in from elsewhere, or one whose config has since been reduced. Those bindings are
     * <b>kept</b> (SC-120 §6.3 rule 1: a slot is never reused or recomputed), so raising the config
     * and restarting restores them exactly; what they cannot do meanwhile is resolve to a block.
     */
    private static void reportSlotsOutsideThePool(
            SlotPool registered, BlockLedger ledger, Path directory) {
        registered.shortfallAgainst(ledger.requiredPool()).forEach((sizeClass, needed) ->
                System.out.println("[SweetCookie] SCE-4013 " + directory
                        + " has bindings outside the registered block pool. Set"
                        + " sweetcookie.blockPool." + sizeClass + " = " + needed
                        + " (currently " + registered.capacity(sizeClass)
                        + ") in config/sweetcookie.json and restart. Those blocks stay bound and"
                        + " unresolved until then; nothing is lost."));
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
