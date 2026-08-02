package net.nennneko5787.lepus.core.format.text;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.json.CanonicalJson;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonString;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockId;

/**
 * How a Bedrock name reaches a Java client in that client's own language. SC-170, SC-100 §9.
 *
 * <p><b>The name must not be resolved on the server.</b> Picking a string out of a lang file at bind
 * time bakes one language into the item and every client sees it, whatever their own setting — which
 * is what a placeholder that formatted the identifier did, and it is wrong in a way that only shows
 * up when someone plays in a language other than the one it was written in.
 *
 * <p>So the two halves are split the way Minecraft splits them: the <b>key</b> travels in the item,
 * and the <b>translations</b> travel in the generated resource pack. The client resolves it. That
 * also means a language added to a pack works without touching the item.
 *
 * <p>Bedrock keys its names {@code tile.<identifier>.name} and {@code item.<identifier>.name}. Ours
 * are keyed by the logical identifier instead, because two packs may define the same Bedrock
 * identifier and SC-120 §3 already resolved that collision — reusing Bedrock's key here would
 * reintroduce it.
 */
@SpecImpl({"SC-170", "SC-100"})
public final class DisplayNames {

    private DisplayNames() {
    }

    /** Bedrock's lang key for a block's name. */
    public static String blockKey(BedrockId identifier) {
        return "tile." + identifier + ".name";
    }

    /** Bedrock's lang key for an item's name. */
    public static String itemKey(BedrockId identifier) {
        return "item." + identifier + ".name";
    }

    /** The key we put in the item and in the generated lang files. */
    public static String javaKey(String logicalId) {
        return "lepus.name." + logicalId;
    }

    /**
     * Bedrock's locale name as Java spells it.
     *
     * <p>{@code ja_JP} becomes {@code ja_jp}. The two schemes agree on the language and region parts
     * and disagree only on case for every locale either engine actually ships, so this is a
     * lowercase rather than a table — and a table of 100 rows that is right 100 times is worth less
     * than one line that is right 100 times.
     */
    public static String javaLocale(String bedrockLocale) {
        return bedrockLocale.toLowerCase(Locale.ROOT);
    }

    /** A Java lang file: a flat JSON object of key to translated text. */
    public static String langJson(Map<String, String> entries) {
        Map<String, JsonValue> members = new LinkedHashMap<>();
        entries.forEach((key, value) -> members.put(key, new JsonString(value)));
        return CanonicalJson.pretty(new JsonObject(members));
    }

    /**
     * A readable name from an identifier, for when no pack translated it.
     *
     * <p>{@code kivotos.binah_trophy} becomes {@code Binah Trophy}. This is the <b>fallback</b> and
     * not the answer: it is English-shaped whatever the client's language, which is exactly why it
     * must never be the thing stored in the item.
     */
    public static String readable(String logicalId) {
        String tail = logicalId.substring(logicalId.lastIndexOf('.') + 1);
        StringBuilder name = new StringBuilder();
        for (String word : tail.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            name.append(name.isEmpty() ? "" : " ")
                    .append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }
        return name.isEmpty() ? logicalId : name.toString();
    }
}
