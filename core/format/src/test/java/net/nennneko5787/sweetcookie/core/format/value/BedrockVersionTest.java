package net.nennneko5787.sweetcookie.core.format.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import org.junit.jupiter.api.Test;

/**
 * Engine and {@code format_version} numbers, SC-110 §6.
 *
 * <p>Kept separate from {@link SemanticVersion} because Bedrock's version numbers are not SemVer:
 * {@code 1.8} is a legal {@code format_version} and {@code 1.26.30.5} is a legal engine version, and
 * both would either fail to parse or acquire prerelease semantics under SemVer rules.
 */
@ProvesSpec("SC-110")
class BedrockVersionTest {

    @Test
    @ProvesSpec("SC-110")
    void parsesOneToFourComponents() {
        assertEquals(BedrockVersion.of(1, 0, 0), BedrockVersion.parse("1"));
        assertEquals(BedrockVersion.of(1, 8, 0), BedrockVersion.parse("1.8"));
        assertEquals(BedrockVersion.of(1, 26, 30), BedrockVersion.parse("1.26.30"));
        assertEquals(new BedrockVersion(1, 26, 30, 5), BedrockVersion.parse("1.26.30.5"));
    }

    @Test
    @ProvesSpec("SC-110")
    void rejectsWhatIsNotAVersion() {
        assertTrue(BedrockVersion.tryParse("1.2.3.4.5").isEmpty());
        assertTrue(BedrockVersion.tryParse("1..2").isEmpty());
        assertTrue(BedrockVersion.tryParse("1.2.x").isEmpty());
        assertTrue(BedrockVersion.tryParse("-1").isEmpty());
        assertTrue(BedrockVersion.tryParse("").isEmpty());
        assertTrue(BedrockVersion.tryParse(null).isEmpty());
    }

    @Test
    @ProvesSpec("SC-110")
    void normalisesTheArrayFormOfMinEngineVersion() {
        assertEquals(BedrockVersion.of(1, 16, 0), BedrockVersion.fromArray(List.of(1, 16, 0)));
        assertEquals(BedrockVersion.of(1, 0, 0), BedrockVersion.fromArray(List.of(1)));
    }

    @Test
    @ProvesSpec("SC-110")
    void ordersNumerically() {
        assertTrue(BedrockVersion.parse("1.8.0").compareTo(BedrockVersion.parse("1.12.0")) < 0);
        assertTrue(BedrockVersion.parse("1.21.9").compareTo(BedrockVersion.parse("1.21.10")) < 0);
        assertTrue(BedrockVersion.parse("1.26.30.5").isAtLeast(BedrockVersion.parse("1.26.30")));
        assertFalse(BedrockVersion.parse("1.26.30").isAtLeast(BedrockVersion.parse("1.26.30.5")));
    }

    @Test
    @ProvesSpec("SC-110")
    void alwaysWritesAtLeastThreeComponents() {
        // A format_version rendered as "1.8" in a diagnostic reads as a different version from the
        // "1.8.0" the file declared, and the whole point of the field is that authors compare them.
        assertEquals("1.8.0", BedrockVersion.parse("1.8").toString());
        assertEquals("1.26.30", BedrockVersion.of(1, 26, 30).toString());
        assertEquals("1.26.30.5", BedrockVersion.parse("1.26.30.5").toString());
    }
}
