package net.nennneko5787.sweetcookie.runtime.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.registry.LedgerJson;
import net.nennneko5787.sweetcookie.core.registry.SlotPool;

/**
 * Finds every world's ledger before any world loads. SC-120 §6.2.
 *
 * <p>Only file discovery lives here; what to do with the answer is {@code PoolSizing}, in
 * {@code core/}, where it is testable without a save directory.
 *
 * <p>The awkward part is that at mod init there is no world and no {@code MinecraftServer} to ask.
 * Both layouts are therefore scanned by shape rather than by asking the game:
 *
 * <pre>
 *   &lt;game&gt;/saves/&lt;world&gt;/data/sweetcookie/ledger.json   a client's saves
 *   &lt;game&gt;/&lt;level-name&gt;/data/sweetcookie/ledger.json     a dedicated server's world
 * </pre>
 *
 * <p>One directory level each, so a game directory with hundreds of unrelated folders costs one
 * {@code list} and no recursion.
 */
@SpecImpl("SC-120")
public final class LedgerScan {

    private LedgerScan() {
    }

    /**
     * What every world in this instance requires.
     *
     * <p>A ledger that cannot be read contributes <b>nothing</b> rather than aborting the scan. It
     * will be reported when that world actually loads, where there is a world to name; refusing to
     * start the game because one unrelated save is damaged would be a poor trade.
     */
    public static List<SlotPool> requirements(Path gameDirectory) {
        List<SlotPool> found = new ArrayList<>();
        for (Path root : List.of(gameDirectory.resolve("saves"), gameDirectory)) {
            for (Path world : childrenOf(root)) {
                Path data = world.resolve("data").resolve("sweetcookie");
                if (!Files.isDirectory(data)) {
                    continue;
                }
                try {
                    LedgerJson.read(data).ifPresent(contents -> found.add(contents.pool()));
                } catch (IOException | RuntimeException unreadable) {
                    // Deliberately silent here. WorldLedger reports SCE-4014 with the world in
                    // hand; a second report at startup naming a path would be noise.
                }
            }
        }
        return found;
    }

    private static List<Path> childrenOf(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(directory)) {
            return children.filter(Files::isDirectory).sorted().toList();
        } catch (IOException unreadable) {
            return List.of();
        }
    }
}
