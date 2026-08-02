package net.nennneko5787.lepus.core.format.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import org.junit.jupiter.api.Test;

/**
 * Pack and module versions, SC-100 §4.3.
 *
 * <p>The point of this type is that {@code manifest.json} format 1 and 2 write
 * {@code [major, minor, patch]} while format 3 writes a SemVer string, and nothing downstream should
 * ever learn which it was.
 */
@ProvesSpec("SC-100")
class SemanticVersionTest {

    @Test
    @ProvesSpec("SC-100")
    void normalisesBothManifestShapesToOneValue() {
        assertEquals(SemanticVersion.of(1, 2, 3), SemanticVersion.fromArray(List.of(1, 2, 3)));
        assertEquals(SemanticVersion.of(1, 2, 3), SemanticVersion.parse("1.2.3"));
    }

    @Test
    @ProvesSpec("SC-100")
    void toleratesTheWrongNumberOfArrayElements() {
        // SC-100 §4.3: missing elements default to 0, extras are dropped. The caller reports
        // SCE-1024; refusing here would reject packs that Bedrock itself loads.
        assertEquals(SemanticVersion.of(1, 0, 0), SemanticVersion.fromArray(List.of(1)));
        assertEquals(SemanticVersion.of(0, 0, 0), SemanticVersion.fromArray(List.of()));
        assertEquals(SemanticVersion.of(1, 2, 3), SemanticVersion.fromArray(List.of(1, 2, 3, 4)));
        assertEquals(SemanticVersion.of(1, 0, 3), SemanticVersion.fromArray(Arrays.asList(1, null, 3)));
    }

    @Test
    @ProvesSpec("SC-100")
    void parsesPrereleaseAndBuildMetadata() {
        SemanticVersion v = SemanticVersion.parse("1.2.3-beta.1+exp.sha.5114f85");
        assertEquals(1, v.major());
        assertEquals("beta.1", v.prerelease());
        assertEquals("exp.sha.5114f85", v.build());
        assertTrue(v.isPrerelease());
        assertEquals("1.2.3-beta.1+exp.sha.5114f85", v.toString());
    }

    @Test
    @ProvesSpec("SC-100")
    void rejectsWhatIsNotASemanticVersion() {
        assertTrue(SemanticVersion.tryParse("1.2").isEmpty());
        assertTrue(SemanticVersion.tryParse("1.2.3.4").isEmpty());
        assertTrue(SemanticVersion.tryParse("1.2.x").isEmpty());
        assertTrue(SemanticVersion.tryParse("").isEmpty());
        assertTrue(SemanticVersion.tryParse(null).isEmpty());
        assertTrue(SemanticVersion.tryParse("1.2.3-").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("nope"));
    }

    @Test
    @ProvesSpec("SC-100")
    void ordersBySemverPrecedence() {
        List<String> ascending = List.of(
                "1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta",
                "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0", "1.0.1", "1.1.0", "2.0.0");
        for (int i = 0; i + 1 < ascending.size(); i++) {
            SemanticVersion lower = SemanticVersion.parse(ascending.get(i));
            SemanticVersion higher = SemanticVersion.parse(ascending.get(i + 1));
            assertTrue(lower.compareTo(higher) < 0, lower + " should sort below " + higher);
            assertTrue(higher.compareTo(lower) > 0, higher + " should sort above " + lower);
        }
    }

    @Test
    @ProvesSpec("SC-100")
    void ignoresBuildMetadataForPrecedence() {
        // Required by SemVer 2.0 §10, and it matters here: SC-100 §4.4 picks the highest version
        // when one UUID is loaded twice, and build metadata must not decide that.
        assertEquals(0, SemanticVersion.parse("1.0.0+a").compareTo(SemanticVersion.parse("1.0.0+b")));
        assertEquals(0, SemanticVersion.parse("1.0.0").compareTo(SemanticVersion.parse("1.0.0+b")));
    }

    @Test
    @ProvesSpec("SC-100")
    void comparesLongNumericPrereleaseIdentifiersWithoutOverflowing() {
        SemanticVersion small = SemanticVersion.parse("1.0.0-99999999999999999999");
        SemanticVersion large = SemanticVersion.parse("1.0.0-999999999999999999999");
        assertTrue(small.compareTo(large) < 0);
    }

    @Test
    @ProvesSpec("SC-100")
    void zeroIsWhatAnAbsentVersionBecomes() {
        assertEquals(SemanticVersion.of(0, 0, 0), SemanticVersion.ZERO);
        assertFalse(SemanticVersion.ZERO.isPrerelease());
        assertEquals("0.0.0", SemanticVersion.ZERO.toString());
    }
}
