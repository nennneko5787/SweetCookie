package net.nennneko5787.lepus.core.format.ir.animation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.ir.IrDiagnostics;
import net.nennneko5787.lepus.core.format.ir.ParseContext;
import net.nennneko5787.lepus.core.format.ir.ParserRegistry;
import net.nennneko5787.lepus.core.format.json.JsonArray;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockVersion;
import net.nennneko5787.lepus.core.format.value.Provenance;

/**
 * Reads {@code animations/*.animation.json}. SC-180 §4, SC-110 §3.
 *
 * <p><b>Four shapes for one thing.</b> A channel's value is written as any of a constant vector, a
 * bare number meaning all three axes, a vector of Molang strings, or a map of time to any of those.
 * All four appear in one file in the corpus this was written against, and a reader that knew three
 * of them would drop the fourth silently — an arm that never moves is not an error anywhere.
 */
@SpecImpl("SC-180#animation/bones")
public final class AnimationFiles {

    private static final String ROOT = "animations";

    private static final ParserRegistry<List<AnimationIr>> REGISTRY =
            new ParserRegistry<List<AnimationIr>>("animation")
                    .register(BedrockVersion.of(1, 8, 0), AnimationFiles::parseAnimations);

    private AnimationFiles() {
    }

    /** Every animation in one file, in declaration order. */
    public static List<AnimationIr> parse(JsonObject root, Provenance file, Diagnostics into) {
        return REGISTRY.parse(root, "format_version", file, into).orElse(List.of());
    }

    private static Optional<List<AnimationIr>> parseAnimations(JsonObject root, ParseContext ctx) {
        Optional<JsonObject> animations = root.getObject(ROOT);
        if (animations.isEmpty()) {
            ctx.at(ROOT).reportMissing(ROOT);
            return Optional.of(List.of());
        }
        List<AnimationIr> out = new ArrayList<>();
        ParseContext at = ctx.at(ROOT);
        animations.get().members().forEach((name, value) -> value.asObject().ifPresent(body ->
                out.add(new AnimationIr(
                        name,
                        // Bedrock also writes "hold_on_last_frame" here, which is neither true nor
                        // false in the sense this field means. Read as NOT looping, and recorded in
                        // the ledger rather than silently conflated with either.
                        body.get("loop").flatMap(JsonValue::asBool).orElse(false),
                        body.get("animation_length").flatMap(JsonValue::asNumber)
                                .map(number -> number.floatValue()),
                        bones(body.getObject("bones").orElse(JsonObject.EMPTY), at.at(name)),
                        at.provenance()))));
        return Optional.of(out);
    }

    private static Map<String, AnimationIr.Bone> bones(JsonObject bones, ParseContext ctx) {
        Map<String, AnimationIr.Bone> out = new LinkedHashMap<>();
        bones.members().forEach((name, value) -> value.asObject().ifPresent(body ->
                out.put(name, new AnimationIr.Bone(
                        channel(body.get("rotation").orElse(null), ctx.at(name)),
                        channel(body.get("position").orElse(null), ctx.at(name)),
                        channel(body.get("scale").orElse(null), ctx.at(name))))));
        return out;
    }

    /**
     * One channel, in whichever of the four shapes the pack used.
     *
     * <p>A map is keyed by <b>time as a string</b> — {@code "1.375"} — so the keys are parsed as
     * numbers rather than compared as text. Sorting them as text puts {@code "10.0"} before
     * {@code "2.0"} and plays the animation in the wrong order, which is the sort of thing that
     * looks like a physics bug.
     */
    private static Optional<AnimationIr.Channel> channel(JsonValue value, ParseContext ctx) {
        if (value == null) {
            return Optional.empty();
        }
        Optional<JsonObject> timeline = value.asObject();
        if (timeline.isEmpty()) {
            return keyframe(value, ctx).map(AnimationIr.Channel::constant);
        }
        TreeMap<Float, AnimationIr.Keyframe> frames = new TreeMap<>();
        timeline.get().members().forEach((time, at) -> {
            Float seconds = seconds(time);
            if (seconds == null) {
                ctx.report(IrDiagnostics.FIELD_MALFORMED, time, "not a keyframe time");
                return;
            }
            // A keyframe may itself be an object carrying `pre` and `post` values - a step in the
            // animation. Only `post` is read: it is the value FROM that time onward, which is what
            // an interpolation starting there needs. `pre` is recorded as missing in the ledger.
            JsonValue body = at.asObject()
                    .map(object -> object.members().getOrDefault("post",
                            object.members().getOrDefault("pre", at)))
                    .orElse(at);
            keyframe(body, ctx).ifPresent(frame -> frames.put(seconds, frame));
        });
        return frames.isEmpty() ? Optional.empty() : Optional.of(new AnimationIr.Channel(frames));
    }

    /** One value: three components, a bare number meaning all three, or a vector of expressions. */
    private static Optional<AnimationIr.Keyframe> keyframe(JsonValue value, ParseContext ctx) {
        Optional<Float> scalar = value.asNumber().map(number -> number.floatValue());
        if (scalar.isPresent()) {
            // `"scale": 0` is how a pack hides a bone, and it means all three axes.
            return Optional.of(AnimationIr.Keyframe.of(scalar.get()));
        }
        Optional<JsonArray> vector = value.asArray();
        if (vector.isEmpty() || vector.get().size() != 3) {
            ctx.report(IrDiagnostics.FIELD_MALFORMED, "keyframe", "not a number or three values");
            return Optional.empty();
        }
        List<AnimationIr.Component> components = new ArrayList<>();
        for (JsonValue component : vector.get().values()) {
            Optional<Float> number = component.asNumber().map(n -> n.floatValue());
            if (number.isPresent()) {
                components.add(AnimationIr.Component.of(number.get()));
                continue;
            }
            // Molang, kept as SOURCE. SC-110 §7: the IR must not hold something evaluable.
            components.add(AnimationIr.Component.of(component.asString().orElse("0")));
        }
        return Optional.of(new AnimationIr.Keyframe(components));
    }

    private static Float seconds(String time) {
        try {
            return Float.valueOf(time);
        } catch (NumberFormatException notATime) {
            return null;
        }
    }
}
