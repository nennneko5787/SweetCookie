package net.nennneko5787.lepus.core.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class BedrockVanillaNamesTest {

    @Test
    void theTableIsThere() {
        // Zero would mean the generated resource did not reach the jar, which is a broken build
        // rather than a broken pack - and it would be invisible otherwise, because every lookup
        // would simply answer "no safe answer" and every rename would silently do nothing.
        assertTrue(BedrockVanillaNames.size() > 1000, "only " + BedrockVanillaNames.size());
    }

    @Test
    void aShortNameThatDiffersFromTheJavaPathIsTranslated() {
        assertEquals(Optional.of("totem_of_undying"), BedrockVanillaNames.javaPathOf("totem"));
        // Bedrock's legacy `tile.<block>.<variant>.name` spelling, which is where the two games
        // diverge most and where assuming the names match goes worst wrong.
        assertEquals(Optional.of("white_wool"), BedrockVanillaNames.javaPathOf("wool.white"));
        assertEquals(Optional.of("stone"), BedrockVanillaNames.javaPathOf("stone.stone"));
    }

    @Test
    void aLangKeyIsResolvedWholeSoCallersDoNotEachStripIt() {
        assertEquals(Optional.of("totem_of_undying"),
                BedrockVanillaNames.javaPathOfLangKey("item.totem.name"));
        // `tile.` counts too: a block's item form is spelled that way in Bedrock, and reading only
        // `item.` lost every door and sign.
        assertEquals(Optional.of("white_wool"),
                BedrockVanillaNames.javaPathOfLangKey("tile.wool.white.name"));
        // Anything else is not a name at all.
        assertEquals(Optional.empty(), BedrockVanillaNames.javaPathOfLangKey("item.totem"));
        assertEquals(Optional.empty(),
                BedrockVanillaNames.javaPathOfLangKey("entity.creeper.name"));
    }

    @Test
    void anAmbiguousNameHasNoAnswerRatherThanAGuess() {
        // Bedrock has one `banner_pattern`; Java has nine items called "Banner Pattern". Choosing
        // one would be a fitted constant. The item keeps its vanilla name instead.
        assertEquals(Optional.empty(), BedrockVanillaNames.javaPathOf("banner_pattern"));
        assertEquals(Optional.empty(), BedrockVanillaNames.javaPathOf(null));
        assertEquals(Optional.empty(), BedrockVanillaNames.javaPathOf("nothing_is_called_this"));
    }
}
