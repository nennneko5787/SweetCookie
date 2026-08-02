package net.nennneko5787.lepus.core.format.ir.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.nennneko5787.lepus.core.format.json.Json;
import org.junit.jupiter.api.Test;

class BlockBoxTest {

    private static Optional<BlockBox> box(String json) {
        return BlockBox.of(Json.parse(json));
    }

    @Test
    void anAbsentComponentIsTheWholeBlock() {
        assertEquals(Optional.of(BlockBox.FULL), BlockBox.of(null));
        assertTrue(BlockBox.FULL.isFullCube());
    }

    @Test
    void bedrocksFullCubeLandsOnJavasFullCube() {
        // The whole point of the +8: Bedrock measures X/Z from the block's centre, Java from its
        // corner. Get the sign wrong and every block in every pack is offset by half a block, which
        // no component-name golden would notice.
        assertEquals(Optional.of(BlockBox.FULL),
                box("{ \"origin\": [-8, 0, -8], \"size\": [16, 16, 16] }"));
    }

    @Test
    void aHalfSlabIsReadWhereItWasWritten() {
        assertEquals(Optional.of(new BlockBox(0, 0, 0, 16, 8, 16)),
                box("{ \"origin\": [-8, 0, -8], \"size\": [16, 8, 16] }"));
    }

    @Test
    void anOffCentreBoxKeepsItsOffset() {
        // A post: 4x16x4 in the middle of the block.
        assertEquals(Optional.of(new BlockBox(6, 0, 6, 10, 16, 10)),
                box("{ \"origin\": [-2, 0, -2], \"size\": [4, 16, 4] }"));
    }

    @Test
    void falseMeansNoBoxAtAll() {
        // What a plant or a decorative block writes, and an empty answer here is a real answer:
        // the player walks through it.
        assertEquals(Optional.empty(), box("false"));
    }

    @Test
    void trueMeansTheDefaultCube() {
        assertEquals(Optional.of(BlockBox.FULL), box("true"));
    }

    @Test
    void oneFieldMissingFallsBackToThatFieldsDefault() {
        // Bedrock accepts either alone, and the two fall back independently. Treating a missing
        // `origin` as a missing component would move the box rather than leave it where it was.
        assertEquals(Optional.of(new BlockBox(0, 0, 0, 16, 4, 16)), box("{ \"size\": [16, 4, 16] }"));
        // X is only offset: -4..12 becomes 4..20. Z is mirrored too, so the same Bedrock numbers
        // come out as -4..12. The two axes differing is the mirror showing.
        assertEquals(Optional.of(new BlockBox(4, 2, -4, 20, 18, 12)),
                box("{ \"origin\": [-4, 2, -4] }"));
    }

    @Test
    void aVectorOfTheWrongLengthIsTheDefaultRatherThanAFailure() {
        // Constitution rule 5. Filling the third axis with a zero would give a flat box nobody
        // wrote; the default is at least a shape the author would recognise.
        assertEquals(Optional.of(BlockBox.FULL), box("{ \"size\": [16, 16] }"));
        assertEquals(Optional.of(BlockBox.FULL), box("{ \"origin\": \"middle\" }"));
        assertEquals(Optional.of(BlockBox.FULL), box("42"));
    }

    @Test
    void anOriginOutsideTheBlockIsPulledBackInside() {
        assertEquals(Optional.of(new BlockBox(0, 0, 0, 16, 16, 16)),
                box("{ \"origin\": [-40, -40, -40], \"size\": [16, 16, 16] }"));
        // Clamped, then mirrored on Z only: the X range keeps its side and the Z range changes to
        // the opposite one.
        assertEquals(Optional.of(new BlockBox(16, 16, -16, 32, 32, 0)),
                box("{ \"origin\": [40, 40, 40], \"size\": [16, 16, 16] }"));
    }

    @Test
    void aBoxLargerThanBedrockAllowsIsTruncatedRatherThanRefused() {
        // The 1.875-block cap. Truncating keeps the block; refusing it would be the outcome
        // constitution rule 5 exists to prevent. See BlockBox.CAP on what is and is not observed.
        // Bedrock z -8..22 after the cap; mirrored, that is -14..16 in Java. The box overhangs the
        // block on the side opposite to the one it overhung before, which is what a mirror does.
        // X, which is only offset, still runs 0..30.
        assertEquals(Optional.of(new BlockBox(0, 0, -14, 30, 30, 16)),
                box("{ \"origin\": [-8, 0, -8], \"size\": [64, 64, 64] }"));
    }

    @Test
    void aNegativeSizeCollapsesToNothing() {
        BlockBox collapsed = box("{ \"origin\": [0, 0, 0], \"size\": [-4, -4, -4] }").orElseThrow();
        assertTrue(collapsed.isEmpty());
        assertEquals(new BlockBox(8, 0, 8, 8, 0, 8), collapsed);
    }

    @Test
    void aZeroHeightBoxIsEmptyButNotAbsent() {
        // Different things: absent means the component said `false`, empty means it described a
        // box with no volume. Both stop nothing, but only the first is what the author asked for.
        Optional<BlockBox> flat = box("{ \"origin\": [-8, 0, -8], \"size\": [16, 0, 16] }");
        assertTrue(flat.isPresent());
        assertTrue(flat.orElseThrow().isEmpty());
        assertFalse(flat.orElseThrow().isFullCube());
    }
}
