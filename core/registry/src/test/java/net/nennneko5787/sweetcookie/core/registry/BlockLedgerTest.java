package net.nennneko5787.sweetcookie.core.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import org.junit.jupiter.api.Test;

/**
 * The slot pool, the ledger and schema drift. SC-120 §6.
 *
 * <p>SC-120 opens by saying it governs on-disk formats and that getting it wrong corrupts worlds.
 * These are the tests that stand between the two.
 */
@ProvesSpec("SC-120")
class BlockLedgerTest {

    private static final SlotPool SMALL =
            new SlotPool(Map.of(1, 2, 2, 2, 4, 2, 8, 2));

    private static StateSchema schema(Object... nameThenValues) {
        List<StateSchema.Entry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < nameThenValues.length; i += 2) {
            @SuppressWarnings("unchecked")
            List<String> values = (List<String>) nameThenValues[i + 1];
            entries.add(new StateSchema.Entry((String) nameThenValues[i], "string", values));
        }
        return new StateSchema(entries);
    }

    // ── Index encoding, the half that reaches chunk storage ──────────────────────────────────

    @Test
    @ProvesSpec("SC-120")
    void encodesTheFirstStateAsTheLeastSignificantDigit() {
        StateSchema s = schema("lit", List.of("false", "true"),
                "level", List.of("0", "1", "2", "3"));
        assertEquals(8, s.size());
        assertEquals(0, s.encode(Map.of("lit", "false", "level", "0")));
        assertEquals(1, s.encode(Map.of("lit", "true", "level", "0")));
        assertEquals(2, s.encode(Map.of("lit", "false", "level", "1")));
        assertEquals(7, s.encode(Map.of("lit", "true", "level", "3")));
        for (int i = 0; i < s.size(); i++) {
            assertEquals(i, s.encode(s.decode(i)));
        }
    }

    @Test
    @ProvesSpec("SC-120")
    void appendingAStateLeavesExistingIndicesAlone() {
        // The property the whole digit order exists for, and the reason SC-120 §6.1's original
        // most-significant-first formula was wrong: with it, enabling one trait shifts every block
        // already placed in every world.
        StateSchema before = schema("lit", List.of("false", "true"));
        StateSchema after = schema("lit", List.of("false", "true"),
                "facing", List.of("north", "south", "east", "west"));

        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.decode(i).get("lit"), after.decode(i).get("lit"),
                    "index " + i + " must still mean the same `lit`");
            assertEquals("north", after.decode(i).get("facing"), "the new state takes its default");
        }
    }

    // ── Schema drift, SC-120 §6.4 ────────────────────────────────────────────────────────────

    @Test
    @ProvesSpec("SC-120")
    void remapsByNameAndValueSoReorderingIsLossless() {
        StateSchema before = schema("a", List.of("x", "y"), "b", List.of("p", "q"));
        StateSchema reordered = schema("b", List.of("p", "q"), "a", List.of("x", "y"));

        for (int i = 0; i < before.size(); i++) {
            Map<String, String> was = before.decode(i);
            Map<String, String> now = reordered.decode(before.remapTo(reordered, i));
            assertEquals(was.get("a"), now.get("a"), "state a survives reordering");
            assertEquals(was.get("b"), now.get("b"), "state b survives reordering");
        }
    }

    @Test
    @ProvesSpec("SC-120")
    void dropsARemovedStateAndDefaultsAnAddedOne() {
        StateSchema before = schema("a", List.of("x", "y"), "gone", List.of("1", "2"));
        StateSchema after = schema("a", List.of("x", "y"), "added", List.of("p", "q"));

        int index = before.encode(Map.of("a", "y", "gone", "2"));
        Map<String, String> remapped = after.decode(before.remapTo(after, index));
        assertEquals("y", remapped.get("a"));
        assertEquals("p", remapped.get("added"), "an added state takes its first declared value");
        assertTrue(!remapped.containsKey("gone"));
    }

    @Test
    @ProvesSpec("SC-120")
    void hashesTheSchemaSoDriftIsDetectable() {
        assertEquals(schema("a", List.of("x", "y")).hash(), schema("a", List.of("x", "y")).hash());
        // Reordering VALUES changes the encoding, so it must change the hash.
        assertNotEquals(schema("a", List.of("x", "y")).hash(),
                schema("a", List.of("y", "x")).hash());
        // So does reordering STATES.
        assertNotEquals(schema("a", List.of("x"), "b", List.of("y")).hash(),
                schema("b", List.of("y"), "a", List.of("x")).hash());
        assertTrue(schema("a", List.of("x")).hash().startsWith("sha256:"));
    }

    // ── Allocation ───────────────────────────────────────────────────────────────────────────

    @Test
    @ProvesSpec("SC-120")
    void takesTheSmallestAdequateSizeClass() {
        BlockLedger ledger = new BlockLedger(SlotPool.DEFAULT);
        assertEquals(1, slotOf(ledger.bind("sweetcookie:a.one", "a:one", StateSchema.EMPTY))
                .sizeClass());
        assertEquals(2, slotOf(ledger.bind("sweetcookie:a.two", "a:two",
                schema("s", List.of("p", "q")))).sizeClass());
        assertEquals(8, slotOf(ledger.bind("sweetcookie:a.six", "a:six",
                schema("s", List.of("1", "2", "3", "4", "5", "6")))).sizeClass());
    }

    @Test
    @ProvesSpec("SC-120")
    void isDeterministicAcrossRuns() {
        // SC-120 §12's determinism test: same packs, clean world, two runs, identical ledgers.
        // Allocation iterates in ascending logical-identifier order for exactly this reason.
        assertEquals(ledgerFor().bindings().toString(), ledgerFor().bindings().toString());
    }

    private static BlockLedger ledgerFor() {
        Map<String, Map.Entry<String, StateSchema>> content = new LinkedHashMap<>();
        content.put("sweetcookie:z.last", Map.entry("z:last", StateSchema.EMPTY));
        content.put("sweetcookie:a.first", Map.entry("a:first", schema("s", List.of("p", "q"))));
        content.put("sweetcookie:m.middle", Map.entry("m:middle", StateSchema.EMPTY));
        BlockLedger ledger = new BlockLedger(SlotPool.DEFAULT);
        ledger.bindAll(content);
        return ledger;
    }

    @Test
    @ProvesSpec("SC-120")
    void keepsASlotWhenTheSchemaChangesButStillFits() {
        BlockLedger ledger = new BlockLedger(SlotPool.DEFAULT);
        BlockSlot original = slotOf(
                ledger.bind("sweetcookie:a.b", "a:b", schema("s", List.of("p", "q"))));

        BlockLedger.Outcome outcome =
                ledger.bind("sweetcookie:a.b", "a:b", schema("s", List.of("p", "r")));
        BlockLedger.Outcome.Remapped remapped =
                assertInstanceOf(BlockLedger.Outcome.Remapped.class, outcome);
        assertEquals(original, remapped.binding().slot(), "the slot is kept; placed blocks remap");
        assertEquals(1, remapped.binding().previousSchemas().size(),
                "the old schema is kept so stale chunks still decode");
    }

    @Test
    @ProvesSpec("SC-120")
    void takesALargerSlotWhenTheSchemaOutgrowsItsClass() {
        BlockLedger ledger = new BlockLedger(SlotPool.DEFAULT);
        BlockSlot original = slotOf(
                ledger.bind("sweetcookie:a.b", "a:b", schema("s", List.of("p", "q"))));

        BlockLedger.Outcome.Reallocated moved = assertInstanceOf(
                BlockLedger.Outcome.Reallocated.class,
                ledger.bind("sweetcookie:a.b", "a:b",
                        schema("s", List.of("p", "q"), "t", List.of("1", "2", "3"))));

        assertEquals(original, moved.previousSlot());
        assertNotEquals(original, moved.binding().slot());
        assertEquals(8, moved.binding().slot().sizeClass());

        // The old slot is retained rather than freed: it still holds placed blocks that decode with
        // the old schema, and handing it to unrelated content would turn them into that content.
        BlockSlot next = slotOf(ledger.bind("sweetcookie:c.d", "c:d", schema("s", List.of("p", "q"))));
        assertNotEquals(original, next);
    }

    @Test
    @ProvesSpec("SC-120")
    void reportsAnExhaustedClassWithTheNumbersAnOperatorNeeds() {
        BlockLedger ledger = new BlockLedger(new SlotPool(Map.of(1, 1)));
        assertInstanceOf(BlockLedger.Outcome.Allocated.class,
                ledger.bind("sweetcookie:a.one", "a:one", StateSchema.EMPTY));

        BlockLedger.Outcome.Exhausted exhausted = assertInstanceOf(
                BlockLedger.Outcome.Exhausted.class,
                ledger.bind("sweetcookie:a.two", "a:two", StateSchema.EMPTY));
        assertEquals(1, exhausted.sizeClass());
        assertEquals("sweetcookie:a.two", exhausted.logicalId());
    }

    @Test
    @ProvesSpec("SC-120")
    void restoresASavedLedgerWithoutRecomputingIt() {
        // Rule 1: a logical id in the ledger keeps its slot forever, even while its pack is absent.
        // Re-deriving on load would move content whenever the pack set changed.
        BlockLedger.Binding saved = new BlockLedger.Binding(
                "sweetcookie:a.b", "a:b", new BlockSlot(4, 7), StateSchema.EMPTY, List.of());
        BlockLedger ledger = BlockLedger.restore(SlotPool.DEFAULT, List.of(saved));

        assertEquals(new BlockSlot(4, 7), ledger.binding("sweetcookie:a.b").orElseThrow().slot());
        // And its slot is not handed out again.
        assertNotEquals(new BlockSlot(4, 7),
                slotOf(ledger.bind("sweetcookie:c.d", "c:d", schema("s", List.of("1", "2", "3")))));
    }

    // ── The pool ─────────────────────────────────────────────────────────────────────────────

    @Test
    @ProvesSpec("SC-120")
    void theDefaultPoolMatchesTheDocumentedTable() {
        assertEquals(2012, SlotPool.DEFAULT.totalBlocks());
        assertEquals(56_832, SlotPool.DEFAULT.totalStates());
    }

    @Test
    @ProvesSpec("SC-120")
    void namesTheClassAndTheCountWhenAWorldNeedsMoreThanIsRegistered() {
        // SC-120 §6.2: the pool does not grow to fit a world. A world that needs more keeps its
        // bindings and reports SCE-4013 with the numbers an operator has to put in the config -
        // "does not fit" alone leaves them nothing to do.
        SlotPool required = new SlotPool(Map.of(1, 4096, 16, 1));

        assertFalse(SlotPool.DEFAULT.covers(required));
        assertEquals(Map.of(1, 4096), SlotPool.DEFAULT.shortfallAgainst(required),
                "only the class that does not fit, with the count it needs");

        assertTrue(SlotPool.DEFAULT.covers(new SlotPool(Map.of(16, 1))));
        assertTrue(SlotPool.DEFAULT.shortfallAgainst(new SlotPool(Map.of(16, 1))).isEmpty());
    }

    @Test
    @ProvesSpec("SC-120")
    void aSlotRendersAsAPoolBlockNameAndNothingResemblingABedrockId() {
        // Constitution rule 4: a slot appears in chunk palettes and the ledger, nowhere else.
        assertEquals("sweetcookie:block_16/0037", new BlockSlot(16, 55).toString());
        assertEquals("sweetcookie:block_1/0000", new BlockSlot(1, 0).toString());
    }

    private static BlockSlot slotOf(BlockLedger.Outcome outcome) {
        return switch (outcome) {
            case BlockLedger.Outcome.Allocated a -> a.binding().slot();
            case BlockLedger.Outcome.Unchanged u -> u.binding().slot();
            case BlockLedger.Outcome.Remapped r -> r.binding().slot();
            case BlockLedger.Outcome.Reallocated r -> r.binding().slot();
            case BlockLedger.Outcome.Exhausted e ->
                    throw new AssertionError("exhausted: " + e);
        };
    }
}
