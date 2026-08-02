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
 */
@SpecImpl("SC-150")
public record BlockPhysics(
        Optional<Float> destroySeconds,
        Optional<Float> explosionResistance,
        int lightEmission,
        float friction) {

    /** What a block with none of these components does. Bedrock's own defaults. */
    public static final BlockPhysics DEFAULT =
            new BlockPhysics(Optional.of(0.0f), Optional.of(0.0f), 0, 0.4f);

    private static final BedrockId DESTRUCTIBLE_BY_MINING =
            BedrockId.parse("minecraft:destructible_by_mining");
    private static final BedrockId DESTRUCTIBLE_BY_EXPLOSION =
            BedrockId.parse("minecraft:destructible_by_explosion");
    private static final BedrockId LIGHT_EMISSION = BedrockId.parse("minecraft:light_emission");
    private static final BedrockId FRICTION = BedrockId.parse("minecraft:friction");

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
                // an unbreakable block.
                destructible(components.get(DESTRUCTIBLE_BY_MINING), "seconds_to_destroy", 0.0f),
                destructible(components.get(DESTRUCTIBLE_BY_EXPLOSION), "explosion_resistance",
                        0.0f),
                clamp(intOf(components.get(LIGHT_EMISSION), 0), 0, 15),
                floatOf(components.get(FRICTION), 0.4f));
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
