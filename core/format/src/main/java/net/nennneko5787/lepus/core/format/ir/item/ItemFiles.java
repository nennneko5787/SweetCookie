package net.nennneko5787.lepus.core.format.ir.item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.ir.ParseContext;
import net.nennneko5787.lepus.core.format.ir.ParserRegistry;
import net.nennneko5787.lepus.core.format.ir.UnknownData;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import net.nennneko5787.lepus.core.format.value.BedrockVersion;
import net.nennneko5787.lepus.core.format.value.Provenance;

/**
 * Reads {@code items/*.json}. SC-170, SC-110 §3.
 *
 * <p>Mirrors {@code BlockFiles} deliberately, down to the dispatch: an item file declares a
 * {@code format_version} and the same registry decides how to read it, so a future format lands as
 * one more registration rather than as a branch inside this one.
 */
@SpecImpl("SC-170")
public final class ItemFiles {

    private static final String ROOT = "minecraft:item";

    private static final Set<String> ITEM_KEYS = Set.of("description", "components", "events");

    private static final ParserRegistry<List<ItemDefIr>> REGISTRY =
            new ParserRegistry<List<ItemDefIr>>("item")
                    .register(BedrockVersion.of(1, 10, 0), ItemFiles::parseItem);

    private ItemFiles() {
    }

    public static List<ItemDefIr> parse(JsonObject root, Provenance file, Diagnostics into) {
        return REGISTRY.parse(root, "format_version", file, into).orElse(List.of());
    }

    private static Optional<List<ItemDefIr>> parseItem(JsonObject root, ParseContext ctx) {
        Optional<JsonObject> item = root.getObject(ROOT);
        if (item.isEmpty()) {
            ctx.at(ROOT).reportMissing(ROOT);
            return Optional.of(List.of());
        }
        JsonObject body = item.get();
        ParseContext at = ctx.at(ROOT);

        Optional<JsonObject> description = body.getObject("description");
        if (description.isEmpty()) {
            at.at("description").reportMissing("description");
            return Optional.of(List.of());
        }
        JsonObject desc = description.get();

        String identifier = desc.getString("identifier").orElse("");
        if (identifier.isBlank()) {
            at.at("description").at("identifier").reportMissing("identifier");
            return Optional.of(List.of());
        }

        return Optional.of(List.of(new ItemDefIr(
                BedrockId.parse(identifier),
                categoryOf(desc),
                inCreativeMenu(desc),
                componentsOf(body.getObject("components").orElse(JsonObject.EMPTY)),
                at.provenance(),
                UnknownData.of(body, ITEM_KEYS))));
    }

    /**
     * Whether the creative menu offers this item, from either format's way of saying so.
     *
     * <p><b>The two formats say it differently and reading only one of them hides items.</b> The
     * {@code 1.10}-era shape has a boolean, {@code register_to_creative_menu}; the modern shape
     * dropped it and made <b>declaring a {@code menu_category} the way an item asks to be listed</b>.
     * Reading only the boolean filed every modern item as "did not ask" — in the corpus this was
     * written against, that was 62 pieces of armour that existed, bound, and appeared in no menu.
     *
     * <p>An explicit boolean still wins wherever a pack writes one, in either shape. A pack saying
     * "no" means no, and that is the only reading under which both formats' authors get what they
     * asked for.
     */
    private static boolean inCreativeMenu(JsonObject description) {
        return description.getBool("register_to_creative_menu")
                .orElseGet(() -> description.has("menu_category"));
    }

    /**
     * The creative-menu group, from either spelling.
     *
     * <p>{@code menu_category.category} is the modern one and a bare {@code category} is what the
     * {@code 1.10}–{@code 1.16} formats wrote. Both are still shipped — the first real add-on read
     * against this build used the bare one — and reading only the modern spelling would file every
     * such item under "no category" for a reason its author could not see.
     */
    private static String categoryOf(JsonObject description) {
        return description.getObject("menu_category")
                .flatMap(menu -> menu.getString("category"))
                .or(() -> description.getString("category"))
                .orElse("");
    }

    private static Map<BedrockId, JsonValue> componentsOf(JsonObject components) {
        Map<BedrockId, JsonValue> byId = new LinkedHashMap<>();
        components.members().forEach((name, value) -> byId.put(BedrockId.parse(name), value));
        return byId;
    }
}
