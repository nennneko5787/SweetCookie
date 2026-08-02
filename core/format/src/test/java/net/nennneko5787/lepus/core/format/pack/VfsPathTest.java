package net.nennneko5787.lepus.core.format.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.nennneko5787.lepus.core.api.ProvesSpec;
import org.junit.jupiter.api.Test;

/** Entry-name normalisation and the safety checks on it. SC-100 §3. */
@ProvesSpec("SC-100")
class VfsPathTest {

    private static VfsPath.Inspection inspect(String raw) {
        return VfsPath.inspect(raw, ExtractionLimits.DEFAULT.maxPathLength());
    }

    @Test
    @ProvesSpec("SC-100")
    void normalisesSeparatorsAndRedundantSegments() {
        // Windows tooling writes backslashes. Treating textures\blocks\a.png as one long file name
        // is how a pack ends up looking empty.
        assertEquals("textures/blocks/a.png", VfsPath.normalise("textures\\blocks\\a.png"));
        assertEquals("a/b", VfsPath.normalise("./a//./b/"));
        assertEquals("", VfsPath.normalise("///"));
    }

    @Test
    @ProvesSpec("SC-100")
    void rejectsZipSlip() {
        assertEquals(VfsPath.Rejection.ESCAPES_ROOT, inspect("../evil").rejection().orElseThrow());
        assertEquals(VfsPath.Rejection.ESCAPES_ROOT, inspect("a/../../evil").rejection().orElseThrow());
        assertEquals(VfsPath.Rejection.ESCAPES_ROOT, inspect("a\\..\\evil").rejection().orElseThrow());
    }

    @Test
    @ProvesSpec("SC-100")
    void rejectsAbsoluteAndDriveRootedNames() {
        // The check runs on the RAW name. Normalising first would turn /etc/passwd into the
        // harmless-looking etc/passwd and accept it under a name it never declared.
        assertEquals(VfsPath.Rejection.ESCAPES_ROOT, inspect("/etc/passwd").rejection().orElseThrow());
        assertEquals(VfsPath.Rejection.ESCAPES_ROOT,
                inspect("C:/Windows/system32").rejection().orElseThrow());
        assertEquals(VfsPath.Rejection.ESCAPES_ROOT,
                inspect("C:\\Windows\\system32").rejection().orElseThrow());
    }

    @Test
    @ProvesSpec("SC-100")
    void rejectsOverlongAndDenormalisedNames() {
        assertEquals(VfsPath.Rejection.TOO_LONG,
                VfsPath.inspect("a".repeat(20), 10).rejection().orElseThrow());
        // U+0065 U+0301 is "e" plus a combining acute: NFD, and it folds together with the NFC form
        // on a filesystem while comparing as a different string.
        assertEquals(VfsPath.Rejection.NOT_NORMALISED,
                inspect("caf" + (char) 0x65 + (char) 0x301 + ".png").rejection().orElseThrow());
        assertEquals(VfsPath.Rejection.NOT_NORMALISED,
                inspect("bad" + (char) 0xFFFD + ".png").rejection().orElseThrow());
    }

    @Test
    @ProvesSpec("SC-100")
    void acceptsWhatRealPacksContain() {
        assertTrue(inspect("textures/blocks/foo.png").accepted());
        assertTrue(inspect("Textures/Blocks/Foo.PNG").accepted());
        assertTrue(inspect("entities/a b c.json").accepted());
        assertEquals("entities/wizard.json", inspect("./entities/wizard.json").path());
    }

    @Test
    @ProvesSpec("SC-100")
    void keysAreLowercaseUnderLocaleRoot() {
        // SC-000 §9. Under a Turkish locale the default toLowerCase turns "I" into a dotless i,
        // which changes which paths collide.
        assertEquals("textures/blocks/foo.png", VfsPath.normalisedKey("Textures\\Blocks\\FOO.PNG"));
    }

    @Test
    @ProvesSpec("SC-100")
    void splitsPathsForListingAndDispatch() {
        assertEquals("textures/blocks", VfsPath.parent("textures/blocks/foo.png"));
        assertEquals("", VfsPath.parent("manifest.json"));
        assertEquals("foo.png", VfsPath.fileName("textures/blocks/foo.png"));
        assertEquals("png", VfsPath.extension("textures/blocks/foo.PNG"));
        assertEquals("", VfsPath.extension("LICENSE"));
        assertTrue(VfsPath.isUnder("a/b/c", "a/b"));
        assertTrue(VfsPath.isUnder("a/b/c", ""));
        assertFalse(VfsPath.isUnder("a/bc", "a/b"));
    }
}
