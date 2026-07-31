package net.nennneko5787.sweetcookie.core.format.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import org.junit.jupiter.api.Test;

/** Pack identity, SC-100 §4.4. */
@ProvesSpec("SC-100")
class PackIdTest {

    @Test
    @ProvesSpec("SC-100")
    void parsesAWellFormedUuid() {
        String raw = "c2f2f6f2-1e7c-4f1c-9a52-6ba9a3a4b111";
        assertEquals(PackId.of(UUID.fromString(raw)), PackId.parse(raw).orElseThrow());
        assertEquals(raw, PackId.parse("  " + raw + "  ").orElseThrow().toString());
    }

    @Test
    @ProvesSpec("SC-100")
    void refusesToParseAMalformedUuid() {
        assertTrue(PackId.parse("not-a-uuid").isEmpty());
        assertTrue(PackId.parse("").isEmpty());
        assertTrue(PackId.parse(null).isEmpty());
    }

    @Test
    @ProvesSpec("SC-100")
    void derivesAStableIdentityFromAMalformedOne() {
        // Real packs ship malformed UUIDs often enough that rejecting them would reject useful
        // content. A random replacement would load the pack but break the block ledger on the next
        // restart, so the fallback has to be deterministic rather than merely unique.
        PackId first = PackId.derived("wizardry-pack");
        PackId second = PackId.derived("wizardry-pack");
        assertEquals(first, second);
        assertNotEquals(first, PackId.derived("wizardry-pack "));
        assertFalse(first.isNone());
    }

    @Test
    @ProvesSpec("SC-100")
    void noneIsTheSentinelForContentFromNoPack() {
        assertTrue(PackId.NONE.isNone());
        assertEquals(new UUID(0L, 0L), PackId.NONE.uuid());
    }

    @Test
    @ProvesSpec("SC-100")
    void ordersDeterministically() {
        // SC-100 §5 breaks load-order ties by (source path, header.uuid), so this ordering is part
        // of what makes registration deterministic rather than a convenience.
        PackId low = PackId.of(new UUID(0L, 1L));
        PackId high = PackId.of(new UUID(1L, 0L));
        assertTrue(low.compareTo(high) < 0);
        assertTrue(high.compareTo(low) > 0);
        assertEquals(0, low.compareTo(PackId.of(new UUID(0L, 1L))));
    }
}
