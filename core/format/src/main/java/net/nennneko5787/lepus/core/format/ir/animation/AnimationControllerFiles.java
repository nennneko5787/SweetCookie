package net.nennneko5787.lepus.core.format.ir.animation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.ir.ParseContext;
import net.nennneko5787.lepus.core.format.ir.ParserRegistry;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockVersion;
import net.nennneko5787.lepus.core.format.value.Provenance;

/**
 * Reads {@code animation_controllers/*.json}. SC-180 §5, SC-110 §3.
 *
 * <p><b>An entry of {@code animations} or {@code transitions} has two shapes</b>, the same two
 * {@code scripts.animate} has: a bare string, or a single-entry object whose value is Molang. A
 * transition is always the second — it needs the guard — and an animation may be either.
 */
@SpecImpl("SC-180#animation_controller/states")
public final class AnimationControllerFiles {

    private static final String ROOT = "animation_controllers";

    private static final ParserRegistry<List<AnimationControllerIr>> REGISTRY =
            new ParserRegistry<List<AnimationControllerIr>>("animation_controller")
                    .register(BedrockVersion.of(1, 10, 0), AnimationControllerFiles::parseAll);

    private AnimationControllerFiles() {
    }

    /** Every controller in one file, in declaration order. */
    public static List<AnimationControllerIr> parse(JsonObject root, Provenance file,
            Diagnostics into) {
        return REGISTRY.parse(root, "format_version", file, into).orElse(List.of());
    }

    private static Optional<List<AnimationControllerIr>> parseAll(JsonObject root,
            ParseContext ctx) {
        Optional<JsonObject> controllers = root.getObject(ROOT);
        if (controllers.isEmpty()) {
            ctx.at(ROOT).reportMissing(ROOT);
            return Optional.of(List.of());
        }
        List<AnimationControllerIr> out = new ArrayList<>();
        ParseContext at = ctx.at(ROOT);
        controllers.get().members().forEach((name, value) -> value.asObject().ifPresent(body ->
                out.add(new AnimationControllerIr(
                        name,
                        body.get("initial_state").flatMap(JsonValue::asString)
                                .orElse(AnimationControllerIr.DEFAULT_STATE),
                        states(body.getObject("states").orElse(JsonObject.EMPTY), at.at(name)),
                        at.provenance()))));
        return Optional.of(out);
    }

    private static Map<String, AnimationControllerIr.State> states(JsonObject states,
            ParseContext ctx) {
        Map<String, AnimationControllerIr.State> out = new LinkedHashMap<>();
        states.members().forEach((name, value) -> value.asObject().ifPresent(body ->
                out.put(name, new AnimationControllerIr.State(
                        plays(body, "animations"),
                        transitions(body, ctx.at(name)),
                        body.get("blend_transition").flatMap(JsonValue::asNumber)
                                .map(seconds -> seconds.floatValue())))));
        return out;
    }

    private static List<AnimationControllerIr.Play> plays(JsonObject state, String field) {
        List<AnimationControllerIr.Play> out = new ArrayList<>();
        state.getArray(field).ifPresent(entries -> entries.values().forEach(entry -> {
            entry.asString().ifPresent(name ->
                    out.add(new AnimationControllerIr.Play(name, Optional.empty())));
            entry.asObject().ifPresent(object -> object.members().forEach((name, blend) ->
                    out.add(new AnimationControllerIr.Play(name, expression(blend)))));
        }));
        return out;
    }

    private static List<AnimationControllerIr.Transition> transitions(JsonObject state,
            ParseContext ctx) {
        List<AnimationControllerIr.Transition> out = new ArrayList<>();
        state.getArray("transitions").ifPresent(entries -> entries.values().forEach(entry ->
                entry.asObject().ifPresent(object -> object.members().forEach((to, guard) ->
                        expression(guard).ifPresent(source ->
                                out.add(new AnimationControllerIr.Transition(to, source)))))));
        return out;
    }

    /**
     * A Molang value that a pack may have written as a number.
     *
     * <p>{@code {"gliding": 1}} and {@code {"gliding": "1"}} mean the same thing, and a reader that
     * took only strings would drop a guard that is always true — which reads on screen as a state
     * the entity can never leave.
     */
    private static Optional<String> expression(JsonValue value) {
        return value.asString()
                .or(() -> value.asNumber().map(number -> String.valueOf(number.floatValue())))
                .or(() -> value.asBool().map(flag -> flag ? "1" : "0"));
    }
}
