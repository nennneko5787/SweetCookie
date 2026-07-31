package net.nennneko5787.sweetcookie.core.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The ledger's on-disk format. SC-120 §6.3. */
@ProvesSpec("SC-120")
class LedgerJsonTest {

    private static StateSchema schema() {
        return new StateSchema(List.of(
                new StateSchema.Entry("wizardry:charge", "int", List.of("0", "1", "2", "3")),
                new StateSchema.Entry("wizardry:lit", "bool", List.of("false", "true"))));
    }

    private static BlockLedger ledgerWithOneBlock() {
        BlockLedger ledger = new BlockLedger(SlotPool.DEFAULT);
        ledger.bind("sweetcookie:wizardry.magic_block", "wizardry:magic_block", schema());
        return ledger;
    }

    @Test
    @ProvesSpec("SC-120")
    void roundTripsABinding() {
        BlockLedger.Binding original =
                ledgerWithOneBlock().binding("sweetcookie:wizardry.magic_block").orElseThrow();

        LedgerJson.Contents read = LedgerJson.parse(LedgerJson.render(ledgerWithOneBlock()));
        assertEquals(1, read.bindings().size());
        BlockLedger.Binding restored = read.bindings().get(0);

        assertEquals(original.logicalId(), restored.logicalId());
        assertEquals(original.bedrockId(), restored.bedrockId());
        assertEquals(original.slot(), restored.slot());
        // The hash surviving is what matters: it is what detects drift on the next load, and a
        // round trip that changed it would report drift on every start.
        assertEquals(original.schemaHash(), restored.schemaHash());
    }

    @Test
    @ProvesSpec("SC-120")
    void restoresWithoutRecomputingTheSlot() {
        // SC-120 §6.3 rule 1: a logical id in the ledger keeps its slot forever. Reading has to
        // preserve it rather than re-derive it, or a changed pack set moves placed blocks.
        LedgerJson.Contents read = LedgerJson.parse(LedgerJson.render(ledgerWithOneBlock()));
        BlockLedger restored = BlockLedger.restore(SlotPool.DEFAULT, read.bindings());

        assertEquals(new BlockSlot(8, 0),
                restored.binding("sweetcookie:wizardry.magic_block").orElseThrow().slot());
    }

    @Test
    @ProvesSpec("SC-120")
    void keepsSchemaHistorySoStaleChunksStillDecode() {
        BlockLedger ledger = ledgerWithOneBlock();
        ledger.bind("sweetcookie:wizardry.magic_block", "wizardry:magic_block",
                new StateSchema(List.of(
                        new StateSchema.Entry("wizardry:charge", "int", List.of("0", "1")))));

        LedgerJson.Contents read = LedgerJson.parse(LedgerJson.render(ledger));
        assertEquals(1, read.bindings().get(0).previousSchemas().size());
        assertEquals(schema().hash(), read.bindings().get(0).previousSchemas().get(0).hash());
    }

    @Test
    @ProvesSpec("SC-120")
    void writesAtomicallyAndKeepsTheOldFileAsABackup(@TempDir Path dir) throws IOException {
        LedgerJson.write(dir, ledgerWithOneBlock());
        assertTrue(Files.isRegularFile(dir.resolve(LedgerJson.FILE_NAME)));
        assertFalse(Files.isRegularFile(dir.resolve(LedgerJson.BACKUP_NAME)),
                "there is nothing to back up on the first write");
        assertFalse(Files.exists(dir.resolve(LedgerJson.FILE_NAME + ".tmp")),
                "the temporary file is renamed, not left behind");

        BlockLedger second = ledgerWithOneBlock();
        second.bind("sweetcookie:a.b", "a:b", StateSchema.EMPTY);
        LedgerJson.write(dir, second);

        assertTrue(Files.isRegularFile(dir.resolve(LedgerJson.BACKUP_NAME)));
        assertEquals(1, LedgerJson.read(dir).orElseThrow().bindings().size() - 1);
    }

    @Test
    @ProvesSpec("SC-120")
    void fallsBackToTheBackupWhenThePrimaryIsDamaged(@TempDir Path dir) throws IOException {
        LedgerJson.write(dir, ledgerWithOneBlock());
        BlockLedger second = ledgerWithOneBlock();
        second.bind("sweetcookie:a.b", "a:b", StateSchema.EMPTY);
        LedgerJson.write(dir, second);

        Files.writeString(dir.resolve(LedgerJson.FILE_NAME), "{ truncated", StandardCharsets.UTF_8);

        // A world with a damaged ledger and a good backup is recoverable; refusing would make it
        // not. The caller reports SCE-4014 - using the backup silently would hide the damage.
        LedgerJson.Contents recovered = LedgerJson.read(dir).orElseThrow();
        assertEquals(1, recovered.bindings().size(), "the backup is the previous revision");
    }

    @Test
    @ProvesSpec("SC-120")
    void aNewWorldHasNoLedgerAndThatIsNotAProblem(@TempDir Path dir) throws IOException {
        assertTrue(LedgerJson.read(dir).isEmpty());
    }

    @Test
    @ProvesSpec("SC-120")
    void refusesALedgerFromAnUnknownFormatVersion() {
        // Guessing at a newer build's allocation hands placed blocks to the wrong content.
        String forward = LedgerJson.render(ledgerWithOneBlock())
                .replace("\"formatVersion\": 1", "\"formatVersion\": 99");
        assertThrows(IllegalArgumentException.class, () -> LedgerJson.parse(forward));
    }

    @Test
    @ProvesSpec("SC-120")
    void recordsThePoolTheWorldRequiresRatherThanTheOneConfigured() {
        // SC-120 §6.2's element-wise maximum needs to know what the WORLD needs; writing the
        // configured pool would make a world demand whatever the machine that saved it had.
        LedgerJson.Contents read = LedgerJson.parse(LedgerJson.render(ledgerWithOneBlock()));
        assertEquals(1, read.pool().capacity(8), "one slot of class 8 is in use");
        assertEquals(0, read.pool().capacity(4096), "and nothing of any other class");
    }

    @Test
    @ProvesSpec("SC-120")
    void isIndentedSoAHumanCanDiffIt() {
        String rendered = LedgerJson.render(ledgerWithOneBlock());
        assertTrue(rendered.contains("\n"), "a one-line ledger is unreviewable");
        assertTrue(rendered.contains("\"stateSchemaHash\""));
    }
}
