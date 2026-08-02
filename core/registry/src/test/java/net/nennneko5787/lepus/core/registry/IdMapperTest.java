package net.nennneko5787.lepus.core.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import org.junit.jupiter.api.Test;

/** Logical identifier derivation. SC-120 §3. */
@ProvesSpec("SC-120")
class IdMapperTest {

    private static BedrockId id(String s) {
        return BedrockId.parse(s);
    }

    @Test
    @ProvesSpec("SC-120")
    void derivesTheDocumentedExamples() {
        assertEquals("lepus:wizardry.magic_wand", IdMapper.logicalIdOf(id("wizardry:magic_wand")));
        assertEquals("lepus:my_pack.fire_ember_block",
                IdMapper.logicalIdOf(id("my_pack:fire/ember_block")));
        // The hyphen survives: it is inside [a-z0-9_.-] and legal in a Java namespace. Mapping it
        // to `_` would manufacture collisions between `cool-pack` and `cool_pack` that need not
        // exist. SC-120 §3's example table said otherwise and contradicted its own rule.
        assertEquals("lepus:cool-pack.thing", IdMapper.logicalIdOf(id("Cool-Pack:Thing")));
    }

    @Test
    @ProvesSpec("SC-120")
    void defaultsAnAbsentNamespaceToMinecraft() {
        assertEquals("lepus:minecraft.stone", IdMapper.logicalIdOf(id("stone")));
    }

    @Test
    @ProvesSpec("SC-120")
    void keepsTheCharactersAJavaIdentifierPermitsAndReplacesTheRest() {
        assertEquals("lepus:a-b_c.0.d_e_f", IdMapper.logicalIdOf(id("a-b_c.0:d/e f")));
    }

    @Test
    @ProvesSpec("SC-120")
    void isPure() {
        // Same input, same output, on every machine and every run: the ledger records what this
        // returns, so a derivation that varied would move content between slots.
        assertEquals(IdMapper.logicalIdOf(id("wizardry:magic_wand")),
                IdMapper.logicalIdOf(id("Wizardry:Magic_Wand")));
    }

    @Test
    @ProvesSpec("SC-120")
    void suffixesTheLaterOfTwoCollidingIdentifiers() {
        // `a/b` and `a b` both sanitise to `a_b`. Load order decides which keeps the plain form.
        BedrockId first = id("pack:a/b");
        BedrockId second = id("pack:a b");
        assertTrue(IdMapper.collide(first, second));

        Map<BedrockId, String> resolved = IdMapper.resolve(List.of(first, second));
        assertEquals("lepus:pack.a_b", resolved.get(first));
        assertNotEquals("lepus:pack.a_b", resolved.get(second));
        assertTrue(resolved.get(second).startsWith("lepus:pack.a_b_h"));

        // Reversing load order reverses who wins, which is why the mapping is written to the ledger
        // rather than re-derived: an existing world keeps its assignment.
        Map<BedrockId, String> reversed = IdMapper.resolve(List.of(second, first));
        assertEquals("lepus:pack.a_b", reversed.get(second));
        assertTrue(reversed.get(first).startsWith("lepus:pack.a_b_h"));
    }

    @Test
    @ProvesSpec("SC-120")
    void hashesTheOriginalIdentifierNotTheSanitisedOne() {
        // The sanitised forms are equal - that is why there is a collision - so hashing them would
        // give both losers the same suffix and not resolve anything.
        Map<BedrockId, String> resolved =
                IdMapper.resolve(List.of(id("pack:a/b"), id("pack:a b"), id("pack:a-b")));
        assertEquals(3, resolved.values().stream().distinct().count());
    }

    @Test
    @ProvesSpec("SC-120")
    void leavesANonCollidingSetAlone() {
        Map<BedrockId, String> resolved =
                IdMapper.resolve(List.of(id("a:one"), id("a:two"), id("b:one")));
        assertFalse(resolved.values().stream().anyMatch(v -> v.contains("_h")));
        assertEquals(3, resolved.size());
    }

    @Test
    @ProvesSpec("SC-120")
    void treatsARepeatedIdentifierAsItself() {
        // The same Bedrock identifier appearing twice is one piece of content overridden by a later
        // pack, not a collision. Suffixing the second copy would give the override a new identity
        // and orphan every block already placed as the first.
        Map<BedrockId, String> resolved =
                IdMapper.resolve(List.of(id("pack:thing"), id("pack:thing")));
        assertEquals(Map.of(id("pack:thing"), "lepus:pack.thing"), resolved);
    }
}
