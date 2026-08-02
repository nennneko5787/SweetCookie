package net.nennneko5787.lepus.core.format.ir.block;

import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockId;

/**
 * The behaviour of one resolved block state, read out of its components. SC-150 §1.
 *
 * <p>Components live in the IR as raw JSON keyed by identifier, because Bedrock has 32 of them and
 * typing all 32 at once would be a large change made before any of them is exercised. This types the
 * handful that decide how a block behaves in the world, and does it here — in {@code core}, with no
 * Minecraft anywhere — so the reading is testable in milliseconds and the Minecraft side only has to
 * apply the numbers.
 *
 * <p><b>Every field has a Bedrock default and a bad value is a default, never a failure.</b> A pack
 * that writes {@code "light_emission": "bright"} gets a dark block and keeps everything else;
 * refusing the block over one typo is the outcome constitution rule 5 exists to prevent.
 *
 * @param destroySeconds  how long to mine, or empty when the block is indestructible by mining
 * @param explosionResistance blast resistance; empty when explosions cannot destroy it
 * @param lightEmission   0-15
 * @param friction        0 to 1; Bedrock's default is 0.4, which is ice-like compared to Java's 0.6
 * @param collision       what the block is solid against, or empty when nothing collides with it
 * @param selection       what the cursor can target, or empty when the block cannot be targeted
 */
@SpecImpl("SC-150")
public record BlockPhysics(
        Optional<Float> destroySeconds,
        Optional<Float> explosionResistance,
        int lightEmission,
        float friction,
        Optional<BlockBox> collision,
        Optional<BlockBox> selection) {

    /** What a block with none of these components does. Bedrock's own defaults. */
    public static final BlockPhysics DEFAULT = new BlockPhysics(Optional.of(0.0f),
            Optional.of(0.0f), 0, 0.4f, Optional.of(BlockBox.FULL), Optional.of(BlockBox.FULL));

    private static final BedrockId DESTRUCTIBLE_BY_MINING =
            BedrockId.parse("minecraft:destructible_by_mining");
    private static final BedrockId DESTRUCTIBLE_BY_EXPLOSION =
            BedrockId.parse("minecraft:destructible_by_explosion");
    private static final BedrockId LIGHT_EMISSION = BedrockId.parse("minecraft:light_emission");
    private static final BedrockId FRICTION = BedrockId.parse("minecraft:friction");
    private static final BedrockId COLLISION_BOX = BedrockId.parse("minecraft:collision_box");
    private static final BedrockId SELECTION_BOX = BedrockId.parse("minecraft:selection_box");

    /**
     * The {@code 1.13}-era spellings of three of these, which are still shipped constantly.
     *
     * <p>Not deprecated aliases to be tidied away later: a block file declaring
     * {@code format_version: "1.13.0"} writes these and nothing else, and a reader that knows only
     * the modern names gives it zero hardness and no light with nothing in the file to explain it.
     *
     * <p>{@code block_light_emission} is the one that would be wrong rather than absent:
     * <b>it is 0 to 1</b> where {@code light_emission} is 0 to 15, so reading it as the modern
     * component turns a lamp at {@code 0.3} into a block emitting nothing.
     */
    private static final BedrockId DESTROY_TIME = BedrockId.parse("minecraft:destroy_time");
    private static final BedrockId BLOCK_LIGHT_EMISSION =
            BedrockId.parse("minecraft:block_light_emission");
    private static final BedrockId EXPLOSION_RESISTANCE =
            BedrockId.parse("minecraft:explosion_resistance");

    /**
     * Reads one resolved state's components.
     *
     * @param components the output of {@code BlockDefIr.resolve(index)} — permutations already
     *                   applied, so nothing here evaluates Molang or knows that permutations exist
     */
    public static BlockPhysics of(Map<BedrockId, JsonValue> components) {
        return new BlockPhysics(
                // Both destructible components take either an object with a number, or `false`
                // meaning "not destructible that way" - the shorthand Bedrock packs actually use for
                // an unbreakable block. The legacy spelling is a bare number and wins nothing: it is
                // consulted only when the modern component is absent.
                components.containsKey(DESTRUCTIBLE_BY_MINING)
                        ? destructible(components.get(DESTRUCTIBLE_BY_MINING),
                                "seconds_to_destroy", 0.0f)
                        : Optional.of(floatOf(components.get(DESTROY_TIME), 0.0f)),
                components.containsKey(DESTRUCTIBLE_BY_EXPLOSION)
                        ? destructible(components.get(DESTRUCTIBLE_BY_EXPLOSION),
                                "explosion_resistance", 0.0f)
                        : Optional.of(floatOf(components.get(EXPLOSION_RESISTANCE), 0.0f)),
                lightOf(components),
                floatOf(components.get(FRICTION), 0.4f),
                // Read SEPARATELY rather than one box serving both. Java falls the collision shape
                // back to the outline shape by default, so sharing here would make
                // `"selection_box": false` silently delete the block's collision as well.
                BlockBox.of(components.get(COLLISION_BOX)),
                BlockBox.of(components.get(SELECTION_BOX)));
    }

    /**
     * Every component this class reads, in either spelling.
     *
     * <p>Exported so that {@code AddonSurvey} can report what a real pack uses and this build
     * ignores <b>without keeping its own list</b>. A second list would drift, and it would drift
     * silently in the direction of claiming more than is true.
     */
    public static final java.util.Set<BedrockId> READS = java.util.Set.of(
            DESTRUCTIBLE_BY_MINING, DESTRUCTIBLE_BY_EXPLOSION, LIGHT_EMISSION, FRICTION,
            COLLISION_BOX, SELECTION_BOX, DESTROY_TIME, BLOCK_LIGHT_EMISSION, EXPLOSION_RESISTANCE);

    /**
     * Light, from whichever spelling the pack used.
     *
     * <p>The legacy one is a <b>fraction of full brightness</b> and the modern one is a level, so
     * {@code "block_light_emission": 0.3} is level 4 or 5 and reading it as a level is a lamp that
     * emits nothing. Rounding rather than truncating: 0.9 is a bright block and level 13 is closer
     * to what its author saw than level 13.5 rounded down would suggest.
     */
    private static int lightOf(Map<BedrockId, JsonValue> components) {
        if (components.containsKey(LIGHT_EMISSION)) {
            return clamp(intOf(components.get(LIGHT_EMISSION), 0), 0, 15);
        }
        JsonValue legacy = components.get(BLOCK_LIGHT_EMISSION);
        return legacy == null
                ? 0
                : clamp(Math.round(floatOf(legacy, 0.0f) * 15.0f), 0, 15);
    }

    private static Optional<Float> destructible(JsonValue component, String field, float fallback) {
        if (component == null) {
            return Optional.of(fallback);
        }
        if (component.asBool().orElse(true) == Boolean.FALSE) {
            return Optional.empty();
        }
        return Optional.of(component.asObject()
                .map(object -> floatOf(object.members().get(field), fallback))
                .orElse(fallback));
    }

    private static float floatOf(JsonValue value, float fallback) {
        return value == null
                ? fallback
                : value.asNumber().map(number -> number.floatValue()).orElse(fallback);
    }

    private static int intOf(JsonValue value, int fallback) {
        return value == null
                ? fallback
                : value.asNumber().map(number -> number.intValue()).orElse(fallback);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** True when mining cannot break this block, whatever the tool. */
    public boolean unbreakable() {
        return destroySeconds.isEmpty();
    }

    /**
     * Minecraft's hardness, from Bedrock's seconds-to-destroy.
     *
     * <p>Not the same quantity: Bedrock states a time, Java states a hardness that a tool's speed
     * divides into. They are close enough at a factor of one for the values packs actually use, and
     * <b>this is the approximation to revisit first</b> when mining speed is compared against
     * Bedrock rather than eyeballed. Recorded here rather than in a TODO nobody reads.
     */
    public float hardness() {
        return destroySeconds.orElse(-1.0f);
    }
}
