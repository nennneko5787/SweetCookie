package net.nennneko5787.sweetcookie.core.format.ir.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.nennneko5787.sweetcookie.core.format.ir.BehaviorIr;
import net.nennneko5787.sweetcookie.core.format.ir.UnknownData;
import net.nennneko5787.sweetcookie.core.format.value.BedrockId;
import net.nennneko5787.sweetcookie.core.format.value.PackId;
import net.nennneko5787.sweetcookie.core.format.value.Provenance;
import org.junit.jupiter.api.Test;

class MenuOrderTest {

    private static BlockDefIr block(String identifier, String menuCategory) {
        BedrockId id = BedrockId.parse(identifier);
        return new BlockDefIr(id, BlockStateSchema.EMPTY, List.of(), Map.of(), List.of(),
                menuCategory, Provenance.file(PackId.NONE, identifier), UnknownData.EMPTY);
    }

    private static BehaviorIr pack(BlockDefIr... blocks) {
        Map<BedrockId, BlockDefIr> map = new LinkedHashMap<>();
        for (BlockDefIr block : blocks) {
            map.put(block.identifier(), block);
        }
        return new BehaviorIr(map);
    }

    private static List<String> names(List<BedrockId> ids) {
        return ids.stream().map(BedrockId::toString).toList();
    }

    @Test
    void eachPacksBlocksStayTogether() {
        // The whole point of one tab rather than one per pack: a pack's run is contiguous, so
        // disabling it removes a block of the list rather than entries scattered through it.
        List<BedrockId> order = MenuOrder.of(List.of(
                pack(block("a:one", "items"), block("a:two", "construction")),
                pack(block("b:one", "construction"))));
        assertEquals(List.of("a:two", "a:one", "b:one"), names(order));
    }

    @Test
    void bedrocksOwnCategoryOrdersWithinAPack() {
        List<BedrockId> order = MenuOrder.of(List.of(pack(
                block("p:d", "items"),
                block("p:b", "nature"),
                block("p:a", "construction"),
                block("p:c", "equipment"))));
        assertEquals(List.of("p:a", "p:b", "p:c", "p:d"), names(order));
    }

    @Test
    void aBlockWithNoCategorySortsLastRatherThanFirst() {
        // A block whose author left the field off must not lead the pack's run ahead of the ones
        // that asked to be there.
        List<BedrockId> order = MenuOrder.of(List.of(pack(
                block("p:missing", ""),
                block("p:stated", "items"))));
        assertEquals(List.of("p:stated", "p:missing"), names(order));
    }

    @Test
    void anUnknownCategoryIsTreatedAsAbsentRatherThanAsAnError() {
        List<BedrockId> order = MenuOrder.of(List.of(pack(
                block("p:odd", "commands"),
                block("p:known", "nature"))));
        assertEquals(List.of("p:known", "p:odd"), names(order));
    }

    @Test
    void categoriesAreMatchedWithoutCaseOrPadding() {
        List<BedrockId> order = MenuOrder.of(List.of(pack(
                block("p:b", " Items "),
                block("p:a", "CONSTRUCTION"))));
        assertEquals(List.of("p:a", "p:b"), names(order));
    }

    @Test
    void twoBlocksInOneCategoryAreOrderedByIdentifierSoTheListIsStable() {
        List<BedrockId> order = MenuOrder.of(List.of(pack(
                block("p:zeta", "nature"), block("p:alpha", "nature"))));
        assertEquals(List.of("p:alpha", "p:zeta"), names(order));
    }

    @Test
    void anOverriddenBlockKeepsThePositionItFirstAppearedAt() {
        // SC-100 section 5 gives the later pack the definition. It does not give it the position:
        // a block a player already knows should stay where they last saw it.
        List<BedrockId> order = MenuOrder.of(List.of(
                pack(block("a:one", "construction")),
                pack(block("b:one", "construction"), block("a:one", "items"))));
        assertEquals(List.of("a:one", "b:one"), names(order));
    }

    @Test
    void noEnabledPacksIsAnEmptyTabRatherThanAFailure() {
        assertEquals(List.of(), MenuOrder.of(List.of()));
        assertEquals(List.of(), MenuOrder.of(List.of(BehaviorIr.EMPTY)));
    }
}
