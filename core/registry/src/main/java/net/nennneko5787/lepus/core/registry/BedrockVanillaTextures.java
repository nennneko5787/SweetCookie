package net.nennneko5787.lepus.core.registry;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Where a picture Bedrock keeps at one path belongs in Java. SC-120 §2.
 *
 * <p>A pack retexturing a vanilla item ships the file at <b>Bedrock's</b> path —
 * {@code textures/items/totem.png} — and Java wants the same picture at
 * {@code assets/minecraft/textures/item/totem_of_undying.png}. Neither the folder nor the file name
 * agrees, and nothing in the pack says which item it means.
 *
 * <p>Generated beside {@link BedrockVanillaNames} and resolved two ways, both checks rather than
 * guesses: through the texture key's own language entry where there is one, and otherwise by
 * matching the file name against Java's own item paths and taking only an exact hit. Bedrock puts
 * all seven swords under one key and picks by aux value, so the second way is what reaches every
 * common tool and weapon; it drops {@code gold_axe}, because Java spells that {@code golden_axe} and
 * a near-miss is not evidence.
 *
 * <p><b>Java items only.</b> A block's icon is drawn from its model rather than from a sprite, so a
 * pack retexturing a block's item form is asking for something this cannot do by replacing a
 * picture. Such a path is simply absent, and the item keeps its vanilla look.
 */
@SpecImpl("SC-120")
public final class BedrockVanillaTextures {

    private static final String RESOURCE = "/lepus/vanilla-item-textures.tsv";

    private static final Map<String, String> BY_BEDROCK_PATH =
            TableResource.load(BedrockVanillaTextures.class, RESOURCE);

    private BedrockVanillaTextures() {
    }

    /**
     * The Java texture a Bedrock texture path belongs at, or empty when there is no safe answer.
     *
     * @param bedrockPath the path as a pack spells it, <b>without</b> an extension —
     *                    {@code textures/items/totem}
     * @return e.g. {@code item/totem_of_undying}, which is a path under
     *         {@code assets/minecraft/textures/}
     */
    public static Optional<String> javaTextureOf(String bedrockPath) {
        return bedrockPath == null ? Optional.empty()
                : Optional.ofNullable(BY_BEDROCK_PATH.get(bedrockPath));
    }

    /**
     * Every Bedrock path this knows about.
     *
     * <p>For the caller that has a pack rather than a path: asking a pack whether it ships each of
     * these is cheaper than listing everything it does ship and asking about each, and it needs no
     * directory walk over an archive.
     */
    public static Set<String> knownBedrockPaths() {
        return BY_BEDROCK_PATH.keySet();
    }

    public static int size() {
        return BY_BEDROCK_PATH.size();
    }
}
