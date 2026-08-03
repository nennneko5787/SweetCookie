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
                        // `blend_weight` is a number OR Molang - "can be an expression" - and a
                        // component is already both. Absent means one, which is why it stays an
                        // Optional rather than defaulting here: a pack that writes 1.0 and a pack
                        // that writes nothing must not be told apart by anything downstream.
                        body.get("blend_weight").flatMap(AnimationFiles::component),
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
            return values(value, ctx)
                    .map(components -> AnimationIr.Channel.constant(
                            new AnimationIr.Keyframe(components)));
        }
        TreeMap<Float, AnimationIr.Keyframe> frames = new TreeMap<>();
        timeline.get().members().forEach((time, at) -> {
            Float seconds = seconds(time);
            if (seconds == null) {
                ctx.report(IrDiagnostics.FIELD_MALFORMED, time, "not a keyframe time");
                return;
            }
            frame(at, ctx).ifPresent(parsed -> frames.put(seconds, parsed));
        });
        return frames.isEmpty() ? Optional.empty() : Optional.of(new AnimationIr.Channel(frames));
    }

    /**
     * One keyframe, which is either a value or an object wrapping one or two of them.
     *
     * <p><b>{@code pre} is the value the channel arrives at and {@code post} the one it leaves
     * with</b>, so a keyframe carrying both is an instant where the animation steps. Either may
     * stand alone, and then it is both. An earlier reader took `post` and dropped `pre`, which
     * silently turned every step into a ramp — nothing in the surveyed corpus writes one, so nothing
     * showed it.
     *
     * <p>{@code lerp_mode} sits here rather than on the channel, which is how Blockbench writes it
     * and how the corpus has it — 233 occurrences, every one of them {@code catmullrom} beside a
     * {@code post}.
     */
    private static Optional<AnimationIr.Keyframe> frame(JsonValue at, ParseContext ctx) {
        Optional<JsonObject> body = at.asObject();
        if (body.isEmpty()) {
            return values(at, ctx).map(AnimationIr.Keyframe::new);
        }
        Map<String, JsonValue> members = body.get().members();
        JsonValue pre = members.getOrDefault("pre", members.get("post"));
        JsonValue post = members.getOrDefault("post", members.get("pre"));
        if (pre == null || post == null) {
            // An object with neither is not a keyframe. Reported and dropped, never thrown.
            ctx.report(IrDiagnostics.FIELD_MALFORMED, "keyframe", "neither pre nor post");
            return Optional.empty();
        }
        AnimationIr.LerpMode lerp = body.get().get("lerp_mode")
                .flatMap(JsonValue::asString)
                .filter(mode -> mode.equalsIgnoreCase("catmullrom"))
                .map(mode -> AnimationIr.LerpMode.CATMULLROM)
                .orElse(AnimationIr.LerpMode.LINEAR);
        Optional<List<AnimationIr.Component>> arriving = values(pre, ctx);
        Optional<List<AnimationIr.Component>> leaving = values(post, ctx);
        if (arriving.isEmpty() || leaving.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AnimationIr.Keyframe(arriving.get(), leaving.get(), lerp));
    }

    /** One value: three components, a bare number meaning all three, or a vector of expressions. */
    private static Optional<List<AnimationIr.Component>> values(JsonValue value, ParseContext ctx) {
        Optional<Float> scalar = value.asNumber().map(number -> number.floatValue());
        if (scalar.isPresent()) {
            // `"scale": 0` is how a pack hides a bone, and it means all three axes.
            AnimationIr.Component all = AnimationIr.Component.of(scalar.get());
            return Optional.of(List.of(all, all, all));
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
        return Optional.of(components);
    }

    /** One value that may be either a number or Molang source. */
    private static Optional<AnimationIr.Component> component(JsonValue value) {
        Optional<Float> number = value.asNumber().map(scalar -> scalar.floatValue());
        if (number.isPresent()) {
            return Optional.of(AnimationIr.Component.of(number.get()));
        }
        return value.asString().map(AnimationIr.Component::of);
    }

    private static Float seconds(String time) {
        try {
            return Float.valueOf(time);
        } catch (NumberFormatException notATime) {
            return null;
        }
    }
}
