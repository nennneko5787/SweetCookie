package net.nennneko5787.lepus.runtime.registry;

import java.util.Locale;
import java.util.Map;
import net.minecraft.world.level.block.SoundType;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Bedrock's sound groups, as Java's. SC-150 §4.
 *
 * <p>Both engines group block sounds by material and both name the groups in words, so this is a
 * table rather than a translation. Most names are the same word in both; the ones below are where
 * they differ, and everything unlisted falls through to the name Java uses.
 *
 * <p><b>Falling back to stone is a real answer and a wrong-sounding one.</b> A block whose group is
 * not here sounds like stone, which is what every bound block did before this existed — audible,
 * never silent, and never a crash.
 */
@SpecImpl("SC-150")
final class BoundSounds {

    /**
     * Where the two vocabularies disagree.
     *
     * <p>{@code cloth} is Bedrock's name for wool. {@code grass} in Bedrock means the plant-like
     * group Java calls {@code grass} too, but Bedrock also uses {@code gravel} for what Java calls
     * {@code gravel}, so those need no row. Only genuine differences are listed: a table that
     * repeated the identical names would hide the interesting rows among fifty boring ones.
     */
    private static final Map<String, SoundType> RENAMED = Map.ofEntries(
            Map.entry("cloth", SoundType.WOOL),
            Map.entry("dirt", SoundType.GRAVEL),
            Map.entry("grass", SoundType.GRASS),
            Map.entry("gravel", SoundType.GRAVEL),
            Map.entry("sand", SoundType.SAND),
            Map.entry("snow", SoundType.SNOW),
            Map.entry("wood", SoundType.WOOD),
            Map.entry("stone", SoundType.STONE),
            Map.entry("metal", SoundType.METAL),
            Map.entry("glass", SoundType.GLASS),
            Map.entry("slime", SoundType.SLIME_BLOCK),
            Map.entry("honey_block", SoundType.HONEY_BLOCK),
            Map.entry("anvil", SoundType.ANVIL),
            Map.entry("ladder", SoundType.LADDER),
            Map.entry("lantern", SoundType.LANTERN),
            Map.entry("amethyst_block", SoundType.AMETHYST),
            Map.entry("amethyst_cluster", SoundType.AMETHYST_CLUSTER),
            Map.entry("bamboo", SoundType.BAMBOO),
            Map.entry("bamboo_sapling", SoundType.BAMBOO_SAPLING),
            Map.entry("scaffolding", SoundType.SCAFFOLDING),
            Map.entry("sweet_berry_bush", SoundType.SWEET_BERRY_BUSH),
            Map.entry("nether_wart", SoundType.NETHER_WART),
            Map.entry("netherrack", SoundType.NETHERRACK),
            Map.entry("nether_brick", SoundType.NETHER_BRICKS),
            Map.entry("basalt", SoundType.BASALT),
            Map.entry("soul_sand", SoundType.SOUL_SAND),
            Map.entry("soul_soil", SoundType.SOUL_SOIL),
            Map.entry("coral", SoundType.CORAL_BLOCK),
            Map.entry("vines", SoundType.VINE),
            Map.entry("stem", SoundType.STEM),
            Map.entry("nylium", SoundType.NYLIUM),
            Map.entry("fungus", SoundType.FUNGUS),
            Map.entry("roots", SoundType.ROOTS),
            Map.entry("shroomlight", SoundType.SHROOMLIGHT),
            Map.entry("candle", SoundType.CANDLE),
            Map.entry("copper", SoundType.COPPER),
            Map.entry("deepslate", SoundType.DEEPSLATE),
            Map.entry("powder_snow", SoundType.POWDER_SNOW));

    private BoundSounds() {
    }

    /** The Java sound group for a Bedrock one, or stone when this build does not know it. */
    static SoundType of(String bedrockGroup) {
        return RENAMED.getOrDefault(bedrockGroup.trim().toLowerCase(Locale.ROOT), SoundType.STONE);
    }
}
