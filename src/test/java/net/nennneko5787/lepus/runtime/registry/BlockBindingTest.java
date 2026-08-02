package net.nennneko5787.lepus.runtime.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.nennneko5787.lepus.core.format.ir.BehaviorIr;
import net.nennneko5787.lepus.core.format.ir.UnknownData;
import net.nennneko5787.lepus.core.format.ir.block.BlockDefIr;
import net.nennneko5787.lepus.core.format.ir.block.BlockStateSchema;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.Provenance;
import org.junit.jupiter.api.Test;

/**
 * How enabled packs combine before anything is bound. SC-100 §5, SC-120 §6.
 *
 * <p>The override rule is the part of binding that decides what a player sees when two packs define
 * the same block, and it is plain data — no world, no registry, no Minecraft. The rest of
 * {@code BlockBinding} is the ledger's own allocation, which {@code core/registry} tests directly.
 */
class BlockBindingTest {

    private static BlockDefIr block(String identifier, String menuCategory) {
        BedrockId id = BedrockId.parse(identifier);
        return new BlockDefIr(id, BlockStateSchema.EMPTY, List.of(), Map.of(), List.of(),
                menuCategory, Provenance.file(PackId.NONE, identifier), UnknownData.EMPTY);
    }

    private static BehaviorIr pack(BlockDefIr... blocks) {
        return new BehaviorIr(java.util.Arrays.stream(blocks)
                .collect(java.util.stream.Collectors.toMap(BlockDefIr::identifier, b -> b,
                        (a, b) -> b, java.util.LinkedHashMap::new)));
    }

    @Test
    void blocksFromEveryEnabledPackAreCollected() {
        Map<BedrockId, BlockDefIr> merged = BlockBinding.merge(List.of(
                pack(block("wizardry:magic_block", "construction")),
                pack(block("machines:press", "equipment"))));
        assertEquals(2, merged.size());
    }

    @Test
    void theLaterPackWinsWhenTwoDefineTheSameBlock() {
        // SC-100 §5. The list is lowest priority first, so the SECOND definition is the one a
        // player gets - getting this backwards silently serves the overridden pack's block.
        Map<BedrockId, BlockDefIr> merged = BlockBinding.merge(List.of(
                pack(block("wizardry:magic_block", "construction")),
                pack(block("wizardry:magic_block", "equipment"))));
        assertEquals(1, merged.size());
        assertEquals("equipment", merged.values().iterator().next().menuCategory());
    }

    @Test
    void anOverriddenBlockKeepsItsPlaceInTheOrder() {
        // Position matters because the derived identifier and therefore the slot follow from it.
        // A pack that overrides another must not shuffle what is already placed in the world.
        Map<BedrockId, BlockDefIr> merged = BlockBinding.merge(List.of(
                pack(block("a:one", "construction"), block("b:two", "construction")),
                pack(block("a:one", "equipment"))));
        assertEquals(List.of("a:one", "b:two"),
                merged.keySet().stream().map(BedrockId::toString).toList());
    }

    @Test
    void noEnabledPacksBindsNothing() {
        assertTrue(BlockBinding.merge(List.of()).isEmpty());
    }

    @Test
    void aPackWithNoBlocksContributesNothingAndBreaksNothing() {
        Map<BedrockId, BlockDefIr> merged = BlockBinding.merge(List.of(
                BehaviorIr.EMPTY,
                pack(block("wizardry:magic_block", "construction")),
                BehaviorIr.EMPTY));
        assertEquals(1, merged.size());
    }
}
