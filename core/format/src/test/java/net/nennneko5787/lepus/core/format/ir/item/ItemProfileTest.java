package net.nennneko5787.lepus.core.format.ir.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import org.junit.jupiter.api.Test;

/** One Bedrock item's components, in terms Java can be told. SC-170 §2. */
@ProvesSpec("SC-170")
class ItemProfileTest {

    /** The components of one item, written as a pack writes them. */
    private static ItemProfile profileOf(String components) {
        Map<BedrockId, JsonValue> map = new LinkedHashMap<>();
        Json.parse(components).asObject().orElseThrow().members()
                .forEach((key, value) -> map.put(BedrockId.parse(key), value));
        return ItemProfile.of(map);
    }

    /**
     * The corpus's own armour, verbatim.
     *
     * <p>Copied field for field from an installed add-on rather than invented, because every
     * mistake this class can make is a mistake about what a real pack writes: which key holds the
     * number, whether it is nested, and which of two spellings is the one in circulation.
     */
    @Test
    void readsARealPiecesOfArmour() {
        ItemProfile halo = profileOf("""
                {
                  "minecraft:max_stack_size": 1,
                  "minecraft:icon": "white_halo",
                  "minecraft:display_name": { "value": "item.kivotos:halo_white.name" },
                  "minecraft:durability": {
                    "max_durability": 363,
                    "damage_chance": { "min": 1, "max": 1 }
                  },
                  "minecraft:wearable": {
                    "slot": "slot.armor.head",
                    "protection": 2,
                    "dispensable": true
                  },
                  "minecraft:enchantable": { "slot": "armor_head", "value": 5 }
                }""");

        assertEquals(Optional.of(363), halo.maxDurability());
        assertEquals(Optional.of(2), halo.protection());
        assertEquals(Optional.of(5), halo.enchantValue());
        assertEquals(Optional.of("head"), halo.javaEquipmentSlot());
    }

    /**
     * The component that holds a KEY rather than a name.
     *
     * <p>Eighteen items in the surveyed corpus point at {@code item.spawn_egg.entity.<entity>.name}
     * — a ticket that places an entity is spelled as a spawn egg, and Bedrock keys spawn eggs by
     * the entity rather than by the item. Looking only under the item's own key left every one of
     * them showing its identifier to the player.
     */
    @Test
    void readsTheLangKeyAnItemNamesForItself() {
        assertEquals(Optional.of("item.spawn_egg.entity.kivotos:recruitment.name"),
                profileOf("""
                        {
                          "minecraft:display_name": {
                            "value": "item.spawn_egg.entity.kivotos:recruitment.name"
                          }
                        }""").nameKey());
        // The older shape is a bare string. A pack using it would otherwise lose its name for a
        // reason nothing on screen could explain.
        assertEquals(Optional.of("item.legacy.name"),
                profileOf("{ \"minecraft:display_name\": \"item.legacy.name\" }").nameKey());
        // And an item that says nothing keeps Bedrock's default key, which is the caller's job.
        assertEquals(Optional.empty(), profileOf("{}").nameKey());
    }

    @Test
    void readsTheSpellingRealPacksActuallyUse() {
        // 35 items in the surveyed corpus say `foil` and none says `glint`. Reading only the name
        // the newest documentation gives would look right and do nothing.
        assertEquals(Optional.of(true), profileOf("{ \"minecraft:foil\": true }").glint());
        assertEquals(Optional.of(true), profileOf("{ \"minecraft:glint\": true }").glint());
        // And false is not absent: an item that says `"foil": false` has said something, and
        // treating it as unstated would let a vanilla default answer instead.
        assertEquals(Optional.of(false), profileOf("{ \"minecraft:foil\": false }").glint());
    }

    @Test
    void anAbsentComponentIsAbsentRatherThanZero() {
        // Writing a component the pack never asked for would override whatever the carrier already
        // had, so "said nothing" has to survive as far as the runtime.
        ItemProfile nothing = profileOf("{}");
        assertEquals(Optional.empty(), nothing.maxStackSize());
        assertEquals(Optional.empty(), nothing.maxDurability());
        assertEquals(Optional.empty(), nothing.glint());
        assertFalse(nothing.isWearable());
    }

    @Test
    void durabilityWinsOverStackingBecauseJavaCannotHaveBoth() {
        // An ItemStack carrying max_damage with a max_stack_size above 1 is refused by Java
        // outright. The two ways to reconcile it are not equally wrong: dropping the durability
        // instead would make every piece of armour in the corpus unbreakable.
        ItemProfile both = profileOf("""
                {
                  "minecraft:max_stack_size": 64,
                  "minecraft:durability": { "max_durability": 100 }
                }""");
        assertEquals(Optional.of(1), both.maxStackSize());
        assertEquals(Optional.of(100), both.maxDurability());
    }

    @Test
    void aStackSizeBeyondJavasLimitIsClampedRatherThanRefused() {
        assertEquals(Optional.of(ItemProfile.MAX_STACK_LIMIT),
                profileOf("{ \"minecraft:max_stack_size\": 999 }").maxStackSize());
        assertEquals(Optional.of(1), profileOf("{ \"minecraft:max_stack_size\": 0 }").maxStackSize());
    }

    @Test
    void anEquipmentSlotJavaHasNotGotIsEmptyRatherThanGuessed() {
        // `slot.armor` on its own is legal Bedrock and means any armour slot; Java's equippable
        // names exactly one. Guessing the head would put a pair of boots on somebody's face.
        ItemProfile any = profileOf("{ \"minecraft:wearable\": { \"slot\": \"slot.armor\" } }");
        assertTrue(any.isWearable(), "the pack did ask to wear it");
        assertEquals(Optional.empty(), any.javaEquipmentSlot());
    }

    @Test
    void aComponentOfTheWrongShapeIsIgnoredRatherThanFatal() {
        // Constitution rule 5. A pack writing a number where an object goes must not take a world
        // down; the item simply loses that one property.
        ItemProfile wrong = profileOf("""
                {
                  "minecraft:durability": 100,
                  "minecraft:wearable": "slot.armor.head",
                  "minecraft:max_stack_size": "sixty-four"
                }""");
        assertEquals(Optional.empty(), wrong.maxDurability());
        assertEquals(Optional.empty(), wrong.maxStackSize());
        assertFalse(wrong.isWearable());
    }
}
