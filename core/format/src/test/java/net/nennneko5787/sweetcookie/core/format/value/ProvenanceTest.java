package net.nennneko5787.sweetcookie.core.format.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import net.nennneko5787.sweetcookie.core.format.json.JsonPointer;
import org.junit.jupiter.api.Test;

/** Provenance, SC-110 §4 — constitution rule 8's implementation. */
@ProvesSpec("SC-110")
class ProvenanceTest {

    private static final PackId PACK = PackId.derived("wizardry");

    @Test
    @ProvesSpec("SC-110")
    void namesThePackTheFileAndThePosition() {
        Provenance where = Provenance.file(PACK, "entities/wizard.json")
                .at(JsonPointer.child(JsonPointer.ROOT, "minecraft:entity"));
        assertEquals(PACK, where.pack());
        assertEquals("entities/wizard.json", where.path());
        assertEquals("/minecraft:entity", where.jsonPointer());
        assertTrue(where.toString().endsWith("entities/wizard.json#/minecraft:entity"));
    }

    @Test
    @ProvesSpec("SC-110")
    void recordsBothTheDeclaredAndTheEffectiveVersion() {
        // The authoring tools have shipped this disagreement for years. Keeping both is what makes
        // SCE-1031 reportable at all.
        Provenance where = Provenance.file(PACK, "models/entity/wizard.geo.json")
                .withVersions("1.8.0", "1.12.0");
        assertTrue(where.versionOverridden());
        assertFalse(Provenance.file(PACK, "x.json").withVersions("1.8.0", "1.8.0")
                .versionOverridden());
        assertFalse(Provenance.file(PACK, "x.json").versionOverridden());
    }

    @Test
    @ProvesSpec("SC-110")
    void lossyIsStickyAndTheWithersDoNotClearIt() {
        Provenance lossy = Provenance.file(PACK, "x.json").markLossy();
        assertTrue(lossy.lossy());
        assertSame(lossy, lossy.markLossy());
        assertTrue(lossy.at("/a").lossy());
        assertTrue(lossy.withVersions("1.8.0", "1.12.0").lossy());
    }

    @Test
    @ProvesSpec("SC-110")
    void atIsIdentityWhenThePointerIsUnchanged() {
        // Provenance is allocated per described node; not reallocating on a no-op keeps the cost a
        // few bytes per node rather than an object per node.
        Provenance where = Provenance.file(PACK, "x.json").at("/a");
        assertSame(where, where.at("/a"));
    }

    @Test
    @ProvesSpec("SC-110")
    void noneRendersWithoutClaimingAFile() {
        assertEquals("<no file>", Provenance.NONE.toString());
        assertTrue(Provenance.NONE.pack().isNone());
    }
}
