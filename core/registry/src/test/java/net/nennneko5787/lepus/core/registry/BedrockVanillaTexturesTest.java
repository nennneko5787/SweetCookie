package net.nennneko5787.lepus.core.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class BedrockVanillaTexturesTest {

    @Test
    void theTableIsThere() {
        assertTrue(BedrockVanillaTextures.size() > 100, "only " + BedrockVanillaTextures.size());
    }

    @Test
    void aPathResolvedThroughItsKeysNameIsTranslated() {
        assertEquals(Optional.of("item/totem_of_undying"),
                BedrockVanillaTextures.javaTextureOf("textures/items/totem"));
    }

    @Test
    void aPathInsideAFamilyIsReachedByItsFileName() {
        // Bedrock files all seven swords under one `sword` key and picks by aux value, so the key
        // names nothing a language entry knows. Every common tool and weapon is in one of those.
        assertEquals(Optional.of("item/diamond_sword"),
                BedrockVanillaTextures.javaTextureOf("textures/items/diamond_sword"));
        assertEquals(Optional.of("item/diamond_axe"),
                BedrockVanillaTextures.javaTextureOf("textures/items/diamond_axe"));
    }

    @Test
    void aNearMissIsRefusedRatherThanBent() {
        // Java spells this `golden_axe`. Taking the near-miss would put a pack's gold axe texture
        // on whatever `gold_axe` was assumed to be; refusing leaves the vanilla picture.
        assertEquals(Optional.empty(),
                BedrockVanillaTextures.javaTextureOf("textures/items/gold_axe"));
        assertEquals(Optional.empty(), BedrockVanillaTextures.javaTextureOf(null));
        assertEquals(Optional.empty(),
                BedrockVanillaTextures.javaTextureOf("textures/items/nothing_is_here"));
    }

    @Test
    void everyTargetIsAJavaItemSprite() {
        // A block's icon comes from its model, so a `block/` target would be a picture nothing
        // reads. The generator drops them; this is the assertion that it kept doing so.
        BedrockVanillaTextures.knownBedrockPaths().forEach(path ->
                assertTrue(BedrockVanillaTextures.javaTextureOf(path).orElseThrow()
                        .startsWith("item/"), path));
    }
}
