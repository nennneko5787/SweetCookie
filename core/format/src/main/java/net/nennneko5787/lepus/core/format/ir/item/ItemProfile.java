package net.nennneko5787.lepus.core.format.ir.item;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.json.JsonNumber;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockId;

/**
 * What one Bedrock item's components come to, in terms nothing Minecraft-specific. SC-170 §2.
 *
 * <p><b>The judgement is here and the application is not.</b> Java's data components live in
 * {@code net.minecraft}, which {@code core} may not name, so this is the half that decides — which
 * keys are read, what their defaults are, which spellings a pack may use, what happens when two
 * components contradict each other — and the runtime half is a transcription with no decisions left
 * in it. Everything that can be got wrong is therefore testable in milliseconds without a client.
 *
 * <p><b>Absent is not zero.</b> Every field is an {@link Optional} or a boolean that defaults to
 * Bedrock's default, because "the pack said 64" and "the pack said nothing" reach Java differently:
 * writing a component a pack never asked for would override whatever the carrier item already had.
 *
 * @param maxStackSize  {@code minecraft:max_stack_size}, already reconciled with durability
 * @param maxDurability {@code minecraft:durability.max_durability}
 * @param wearableSlot  where {@code minecraft:wearable} puts it, in Bedrock's spelling
 * @param protection    {@code minecraft:wearable.protection}, Bedrock's own scale
 * @param enchantValue  {@code minecraft:enchantable.value}
 * @param glint         {@code minecraft:foil} or {@code minecraft:glint}
 * @param nameKey       {@code minecraft:display_name.value} — a TRANSLATION KEY, not a name
 */
@SpecImpl({"SC-170#minecraft:max_stack_size", "SC-170#minecraft:durability",
        "SC-170#minecraft:wearable", "SC-170#minecraft:enchantable", "SC-170#minecraft:glint",
        "SC-170#minecraft:foil", "SC-170#minecraft:display_name"})
public record ItemProfile(
        Optional<Integer> maxStackSize,
        Optional<Integer> maxDurability,
        Optional<String> wearableSlot,
        Optional<Integer> protection,
        Optional<Integer> enchantValue,
        Optional<Boolean> glint,
        Optional<String> nameKey) {

    /** An item whose components say nothing this build reads. */
    public static final ItemProfile NONE = new ItemProfile(Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty());

    private static final BedrockId MAX_STACK_SIZE = BedrockId.parse("minecraft:max_stack_size");
    private static final BedrockId DURABILITY = BedrockId.parse("minecraft:durability");
    private static final BedrockId WEARABLE = BedrockId.parse("minecraft:wearable");
    private static final BedrockId ENCHANTABLE = BedrockId.parse("minecraft:enchantable");

    /**
     * {@code minecraft:display_name}, whose value is a <b>lang key</b> and not a name.
     *
     * <p>Bedrock names an item under {@code item.<identifier>.name} by default, and this component
     * points somewhere else. Real packs use it to borrow another entry's name: the corpus this was
     * written against has 18 items — the whole "recruitment" family — naming
     * {@code item.spawn_egg.entity.<entity>.name}, because a ticket that places an entity is
     * spelled as a spawn egg and Bedrock keys spawn eggs by the entity rather than by the item.
     *
     * <p>Resolving it to TEXT here would be wrong (SC-170, {@code DisplayNames}): the key travels to
     * the client and the client picks the language. So this carries the key and nothing else.
     */
    private static final BedrockId DISPLAY_NAME = BedrockId.parse("minecraft:display_name");

    /**
     * Both spellings of the same component.
     *
     * <p>{@code foil} is the older one and {@code glint} the newer, and <b>every item in the
     * surveyed corpus uses {@code foil}</b> — 35 of them, against none using {@code glint}. Reading
     * only the name the newest documentation gives would have looked correct and done nothing.
     */
    private static final BedrockId FOIL = BedrockId.parse("minecraft:foil");
    private static final BedrockId GLINT = BedrockId.parse("minecraft:glint");

    /** Every component this class reads. See {@code BlockPhysics.READS} on why it is exported. */
    public static final Set<BedrockId> READS = Set.of(MAX_STACK_SIZE, DURABILITY, WEARABLE,
            ENCHANTABLE, FOIL, GLINT, DISPLAY_NAME);

    /** Java's own limit. A pack asking for more gets 99 rather than a refused item. */
    public static final int MAX_STACK_LIMIT = 99;

    public static ItemProfile of(Map<BedrockId, JsonValue> components) {
        Optional<Integer> durability = object(components, DURABILITY)
                .flatMap(o -> integer(o, "max_durability"))
                .filter(value -> value > 0);
        Optional<Integer> stack = number(components.get(MAX_STACK_SIZE))
                .map(value -> Math.max(1, Math.min(MAX_STACK_LIMIT, value)));

        // Java cannot have both: a damageable stack is a stack of one, and an ItemStack carrying
        // max_damage with a max_stack_size above 1 is refused outright. Bedrock's own items never
        // ask for both, but a pack may, and the two answers are not equally wrong - dropping the
        // durability instead would make 62 pieces of this corpus's armour unbreakable.
        Optional<Integer> reconciled = durability.isPresent() ? Optional.of(1) : stack;

        Optional<JsonObject> wearable = object(components, WEARABLE);
        return new ItemProfile(
                reconciled,
                durability,
                wearable.flatMap(o -> string(o, "slot")),
                wearable.flatMap(o -> integer(o, "protection")),
                object(components, ENCHANTABLE).flatMap(o -> integer(o, "value")),
                glintOf(components),
                // Both shapes: `{"value": "…"}` is the modern one and a bare string is what the
                // older format wrote. A pack using the older shape would otherwise lose its name
                // for a reason nothing on screen could explain.
                object(components, DISPLAY_NAME).flatMap(o -> string(o, "value"))
                        .or(() -> Optional.ofNullable(components.get(DISPLAY_NAME))
                                .flatMap(JsonValue::asString)
                                .filter(text -> !text.isBlank())));
    }

    /**
     * The equipment slot, as Java would name it, or empty when Bedrock names one Java has not got.
     *
     * <p>Bedrock spells these {@code slot.armor.head}; the runtime half wants {@code head}. Kept
     * here rather than there because it is a mapping and mappings are what this class is for — and
     * an unknown slot answering empty is what keeps an unrecognised value from becoming a crash.
     */
    public Optional<String> javaEquipmentSlot() {
        return wearableSlot.flatMap(slot -> switch (slot.toLowerCase(Locale.ROOT)) {
            case "slot.armor.head" -> Optional.of("head");
            case "slot.armor.chest" -> Optional.of("chest");
            case "slot.armor.legs" -> Optional.of("legs");
            case "slot.armor.feet" -> Optional.of("feet");
            case "slot.weapon.offhand" -> Optional.of("offhand");
            // `slot.armor` on its own is legal Bedrock and means "any armour slot", which Java
            // cannot express: an equippable names exactly one. Reported by the caller rather than
            // guessed at, because guessing the head would put a pair of boots on somebody's face.
            default -> Optional.empty();
        });
    }

    /** True when the pack asked for a slot at all, whether or not Java has one for it. */
    public boolean isWearable() {
        return wearableSlot.isPresent();
    }

    private static Optional<Boolean> glintOf(Map<BedrockId, JsonValue> components) {
        return bool(components.get(FOIL)).or(() -> bool(components.get(GLINT)));
    }

    private static Optional<JsonObject> object(Map<BedrockId, JsonValue> components, BedrockId id) {
        JsonValue value = components.get(id);
        return value == null ? Optional.empty() : value.asObject();
    }

    private static Optional<String> string(JsonObject object, String key) {
        JsonValue value = object.members().get(key);
        return value == null ? Optional.empty() : value.asString().filter(text -> !text.isBlank());
    }

    private static Optional<Integer> integer(JsonObject object, String key) {
        return number(object.members().get(key));
    }

    private static Optional<Integer> number(JsonValue value) {
        return value == null ? Optional.empty() : value.asNumber().map(JsonNumber::intValue);
    }

    private static Optional<Boolean> bool(JsonValue value) {
        return value == null ? Optional.empty() : value.asBool();
    }
}
