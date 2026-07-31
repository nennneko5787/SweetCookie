package net.nennneko5787.sweetcookie.core.registry;

import java.util.Locale;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * A physical slot in the block pool. SC-120 §6.
 *
 * <p><b>A slot is not an identity.</b> It appears in chunk palettes and in the world ledger and
 * nowhere else — never in a specification, a command, an annotation, a packet or anything a human
 * reads. Confusing it with the logical identifier is a review-blocking defect (constitution rule 4),
 * which is why this type is deliberately awkward to render: {@link #toString()} produces the pool
 * block's registry name, and there is no method that produces anything resembling a Bedrock
 * identifier.
 *
 * @param sizeClass the pool block's state count; a power of two
 * @param index     which reserved block of that class, zero-based
 */
@SpecImpl("SC-120")
public record BlockSlot(int sizeClass, int index) implements Comparable<BlockSlot> {

    public BlockSlot {
        if (sizeClass < 1 || Integer.bitCount(sizeClass) != 1) {
            throw new IllegalArgumentException("size class must be a power of two: " + sizeClass);
        }
        if (index < 0) {
            throw new IllegalArgumentException("slot index must not be negative: " + index);
        }
    }

    /** The pool block's registry name, e.g. {@code sweetcookie:block_16/0037}. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s:block_%d/%04x", IdMapper.NAMESPACE, sizeClass, index);
    }

    @Override
    public int compareTo(BlockSlot other) {
        int bySize = Integer.compare(sizeClass, other.sizeClass);
        return bySize != 0 ? bySize : Integer.compare(index, other.index);
    }
}
