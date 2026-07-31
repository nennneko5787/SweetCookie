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
import net.nennneko5787.sweetcookie.core.format.value.PackId;
import net.nennneko5787.sweetcookie.core.format.value.SemanticVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Per-world pack activation and its file. SC-120 §8. */
@ProvesSpec("SC-120")
class ActivePacksTest {

    private static final PackId A = PackId.derived("a");
    private static final PackId B = PackId.derived("b");
    private static final PackId C = PackId.derived("c");
    private static final SemanticVersion V1 = SemanticVersion.of(1, 0, 0);

    private static ActivePacks abc() {
        return ActivePacks.NONE.enable(A, V1).enable(B, V1).enable(C, V1);
    }

    @Test
    @ProvesSpec("SC-120")
    void enablesAtTheEndWhereItOverridesEverythingElse() {
        // A user enabling a pack almost always wants to see it. One that silently lost to a pack
        // enabled earlier would look broken rather than overridden.
        assertEquals(List.of(A, B, C), abc().order());
        assertTrue(abc().isEnabled(B));
        assertEquals(2, abc().orderOf(C).orElseThrow());
    }

    @Test
    @ProvesSpec("SC-120")
    void reEnablingUpdatesTheVersionAndLeavesThePositionAlone() {
        // Moving it would change what overrides what without being asked.
        ActivePacks updated = abc().enable(A, SemanticVersion.of(2, 0, 0));
        assertEquals(List.of(A, B, C), updated.order());
        assertEquals(SemanticVersion.of(2, 0, 0), updated.entries().get(0).version());
        assertEquals(3, updated.size());
    }

    @Test
    @ProvesSpec("SC-120")
    void disablesWithoutDisturbingTheRest() {
        assertEquals(List.of(A, C), abc().disable(B).order());
        assertFalse(abc().disable(B).isEnabled(B));
        assertEquals(abc().order(), abc().disable(PackId.derived("never enabled")).order());
    }

    @Test
    @ProvesSpec("SC-120")
    void movesAndClampsRatherThanRefusing() {
        assertEquals(List.of(C, A, B), abc().moveTo(C, 0).order());
        assertEquals(List.of(B, C, A), abc().moveTo(A, 99).order(),
                "'move it to the top' is naturally typed as a number past the end");
        assertEquals(List.of(A, B, C), abc().moveTo(A, -5).order());
        assertEquals(abc().order(), abc().moveTo(PackId.derived("absent"), 0).order());
    }

    @Test
    @ProvesSpec("SC-120")
    void isImmutableSoAFailedActivationCannotHalfApply() {
        // SC-120 §8 step 1: a parse error aborts the change and leaves the previous set live. With
        // values that is true by construction rather than by remembering to roll back.
        ActivePacks original = abc();
        original.disable(A);
        original.moveTo(C, 0);
        assertEquals(List.of(A, B, C), original.order());
    }

    // ── The file ─────────────────────────────────────────────────────────────────────────────

    @Test
    @ProvesSpec("SC-120")
    void roundTripsThroughItsFile() {
        ActivePacks read = ActiveJson.parse(ActiveJson.render(abc()));
        assertEquals(abc().order(), read.order());
        assertEquals(V1, read.entries().get(0).version());
    }

    @Test
    @ProvesSpec("SC-120")
    void saysInTheFileWhichEndWins() {
        // A user editing active.json by hand has no other way to learn the precedence direction,
        // and guessing wrong silently gives them the other pack's content.
        assertTrue(ActiveJson.render(abc()).contains("the last entry overrides the rest"));
    }

    @Test
    @ProvesSpec("SC-120")
    void writesAtomicallyAndKeepsABackup(@TempDir Path dir) throws IOException {
        ActiveJson.write(dir, abc());
        assertFalse(Files.exists(dir.resolve(ActiveJson.FILE_NAME + ".tmp")));
        ActiveJson.write(dir, abc().disable(B));

        assertTrue(Files.isRegularFile(dir.resolve(ActiveJson.BACKUP_NAME)));
        assertEquals(List.of(A, C), ActiveJson.read(dir).orElseThrow().order());
    }

    @Test
    @ProvesSpec("SC-120")
    void recoversFromADamagedFileRatherThanRefusing(@TempDir Path dir) throws IOException {
        ActiveJson.write(dir, abc());
        ActiveJson.write(dir, abc().disable(C));
        Files.writeString(dir.resolve(ActiveJson.FILE_NAME), "{ truncated", StandardCharsets.UTF_8);

        // Unlike the ledger, this file is short enough for a human to retype, so falling back beats
        // refusing to start the world.
        assertEquals(List.of(A, B, C), ActiveJson.read(dir).orElseThrow().order());
    }

    @Test
    @ProvesSpec("SC-120")
    void aWorldThatNeverActivatedAnythingHasNoFile(@TempDir Path dir) throws IOException {
        assertTrue(ActiveJson.read(dir).isEmpty());
    }

    @Test
    @ProvesSpec("SC-120")
    void refusesAnUnknownFormatVersion() {
        String forward = ActiveJson.render(abc()).replace("\"formatVersion\": 1", "\"formatVersion\": 9");
        assertThrows(IllegalArgumentException.class, () -> ActiveJson.parse(forward));
    }
}
