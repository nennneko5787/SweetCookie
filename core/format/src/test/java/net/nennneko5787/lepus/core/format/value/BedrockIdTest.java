package net.nennneko5787.lepus.core.format.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import net.nennneko5787.lepus.core.api.ProvesSpec;

@ProvesSpec("SC-110")
class BedrockIdTest {

    @Test
    void parsesNamespaceAndPath() {
        BedrockId id = BedrockId.parse("wizardry:magic_wand");
        assertEquals("wizardry", id.namespace());
        assertEquals("magic_wand", id.path());
    }

    @Test
    void defaultsMissingNamespaceToMinecraft() {
        BedrockId id = BedrockId.parse("stone");
        assertEquals(BedrockId.DEFAULT_NAMESPACE, id.namespace());
        assertTrue(id.isVanilla());
    }

    @Test
    void splitsOnTheFirstColonOnly() {
        // Geometry inheritance is written `geometry.a:geometry.b`. Splitting on the last colon
        // would silently mangle it, and the failure would surface as a missing model.
        BedrockId id = BedrockId.parse("geometry.a:geometry.b");
        assertEquals("geometry.a", id.namespace());
        assertEquals("geometry.b", id.path());
    }

    @Test
    void comparesCaseInsensitively() {
        assertEquals(BedrockId.parse("Wizardry:Magic_Wand"), BedrockId.parse("wizardry:magic_wand"));
        assertEquals(
                BedrockId.parse("Wizardry:Magic_Wand").hashCode(),
                BedrockId.parse("wizardry:magic_wand").hashCode());
        assertNotEquals(BedrockId.parse("a:b"), BedrockId.parse("a:c"));
    }

    @Test
    void preservesOriginalSpellingForDiagnostics() {
        // Equality ignores case, but a diagnostic must quote the pack author's own text back to
        // them or they cannot find it in their files.
        assertEquals("Wizardry:Magic_Wand", BedrockId.parse("Wizardry:Magic_Wand").toString());
    }

    @Test
    void caseFoldingIsLocaleIndependent() {
        // In a Turkish locale, "I".toLowerCase() is not "i". If case folding used the default
        // locale, which identifiers collide would depend on the machine's language — and identifier
        // collisions feed the ledger, which feeds saved worlds (SC-000 section 9).
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals(BedrockId.parse("pack:ITEM"), BedrockId.parse("pack:item"));
            assertEquals(
                    BedrockId.parse("pack:ITEM").hashCode(),
                    BedrockId.parse("pack:item").hashCode());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void rejectsEmptyParts() {
        assertThrows(IllegalArgumentException.class, () -> BedrockId.parse(":path"));
        assertThrows(IllegalArgumentException.class, () -> BedrockId.parse("namespace:"));
        assertThrows(IllegalArgumentException.class, () -> BedrockId.parse(""));
    }
}
