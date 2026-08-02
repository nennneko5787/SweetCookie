package net.nennneko5787.lepus.core.format.pack;

import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * The bounds every archive is read under. SC-100 §3.
 *
 * <p>Exceeding one aborts the offending <b>pack</b>, never the whole load: an add-on with one hostile
 * pack in it should still give the user their other four.
 *
 * <p>The defaults are what the conformance corpus asserts, so changing one is a specification change
 * rather than a tuning decision.
 *
 * @param totalUncompressedBytes total decompressed size of one archive
 * @param maxCompressionRatio    decompressed-to-compressed ratio of any single entry
 * @param maxEntries             entries in one archive
 * @param maxNestingDepth        archives within archives
 * @param maxFileBytes           a single decompressed entry
 * @param maxPathLength          an entry name after normalisation
 */
@SpecImpl("SC-100")
public record ExtractionLimits(
        long totalUncompressedBytes,
        int maxCompressionRatio,
        int maxEntries,
        int maxNestingDepth,
        long maxFileBytes,
        int maxPathLength) {

    /** SC-100 §3's table. */
    public static final ExtractionLimits DEFAULT = new ExtractionLimits(
            512L * 1024 * 1024,
            200,
            65_536,
            3,
            64L * 1024 * 1024,
            512);

    public ExtractionLimits {
        if (totalUncompressedBytes < 1 || maxCompressionRatio < 1 || maxEntries < 1
                || maxNestingDepth < 1 || maxFileBytes < 1 || maxPathLength < 1) {
            throw new IllegalArgumentException("every extraction limit must be positive");
        }
    }

    /**
     * True when an entry's declared sizes describe a compression bomb.
     *
     * <p>Declared sizes are attacker-controlled, so this is a cheap first filter and not the
     * defence. The defence is that reads are capped at {@link #maxFileBytes} against the bytes that
     * actually arrive — a lying header buys nothing.
     */
    public boolean isRatioSuspicious(long uncompressed, long compressed) {
        return compressed > 0 && uncompressed / compressed > maxCompressionRatio;
    }
}
