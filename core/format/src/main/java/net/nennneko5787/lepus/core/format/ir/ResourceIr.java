package net.nennneko5787.lepus.core.format.ir;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;

/**
 * The resource-pack half of one pack's IR. SC-110 §8.2.
 *
 * <p><b>One field so far.</b> SC-110 §8.2 lists client entities, render controllers, animations,
 * particles, attachables, materials, atlases and sounds alongside geometry; each arrives with its
 * own domain document and its own conformance cases. An empty map is the honest representation of
 * "not parsed yet" — it is not a claim that the pack has none, and the coverage ledger is where that
 * distinction is recorded.
 *
 * <p>Raw binary assets are deliberately absent (SC-110 §8.2): the IR holds paths and the asset
 * pipeline reads them through the VFS on demand. Loading a 300 MB texture set into memory to find
 * out what is in it would be indefensible.
 *
 * @param geometries keyed by {@code geometry.<name>}, in the order the pack's files were walked
 * @param items      <b>the resource pack's own</b> {@code items/} definitions, which are a different
 *                   file from the behaviour pack's of the same name. Bedrock splits an item in two:
 *                   the behaviour pack says what it does and the resource pack says what it looks
 *                   like, and {@code minecraft:icon} is only ever in the second. Reading only the
 *                   first leaves every item in such a pack with no picture
 */
@SpecImpl("SC-110")
public record ResourceIr(Map<String, GeometryIr> geometries,
        Map<net.nennneko5787.lepus.core.format.value.BedrockId,
                net.nennneko5787.lepus.core.format.ir.item.ItemDefIr> items,
        Map<net.nennneko5787.lepus.core.format.value.BedrockId,
                net.nennneko5787.lepus.core.format.ir.attachable.AttachableIr> attachables,
        Map<String, net.nennneko5787.lepus.core.format.ir.animation.AnimationIr> animations,
        Map<String, net.nennneko5787.lepus.core.format.ir.animation.AnimationControllerIr>
                controllers) {

    public static final ResourceIr EMPTY =
            new ResourceIr(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    public ResourceIr {
        geometries = Collections.unmodifiableMap(new LinkedHashMap<>(geometries));
        items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
        attachables = Collections.unmodifiableMap(new LinkedHashMap<>(attachables));
        animations = Collections.unmodifiableMap(new LinkedHashMap<>(animations));
        controllers = Collections.unmodifiableMap(new LinkedHashMap<>(controllers));
    }

    /** Geometry and items, for the callers that predate attachables. */
    public ResourceIr(Map<String, GeometryIr> geometries,
            Map<net.nennneko5787.lepus.core.format.value.BedrockId,
                    net.nennneko5787.lepus.core.format.ir.item.ItemDefIr> items) {
        this(geometries, items, Map.of(), Map.of(), Map.of());
    }

    /** Everything but the controllers, for the callers written before there were any. */
    public ResourceIr(Map<String, GeometryIr> geometries,
            Map<net.nennneko5787.lepus.core.format.value.BedrockId,
                    net.nennneko5787.lepus.core.format.ir.item.ItemDefIr> items,
            Map<net.nennneko5787.lepus.core.format.value.BedrockId,
                    net.nennneko5787.lepus.core.format.ir.attachable.AttachableIr> attachables,
            Map<String, net.nennneko5787.lepus.core.format.ir.animation.AnimationIr> animations) {
        this(geometries, items, attachables, animations, Map.of());
    }

    /** The animation controller of that name, if any pack declares one. */
    public Optional<net.nennneko5787.lepus.core.format.ir.animation.AnimationControllerIr>
            controller(String name) {
        return Optional.ofNullable(controllers.get(name));
    }

    /** The animation of that name, if any pack declares one. */
    public Optional<net.nennneko5787.lepus.core.format.ir.animation.AnimationIr> animation(
            String name) {
        return Optional.ofNullable(animations.get(name));
    }

    /** The attachable an item of that identifier is held as, if any pack declares one. */
    public Optional<net.nennneko5787.lepus.core.format.ir.attachable.AttachableIr>
            attachable(net.nennneko5787.lepus.core.format.value.BedrockId identifier) {
        return Optional.ofNullable(attachables.get(identifier));
    }

    /** Geometry only, for the callers that predate the client-side item definitions. */
    public ResourceIr(Map<String, GeometryIr> geometries) {
        this(geometries, Map.of(), Map.of(), Map.of(), Map.of());
    }

    public Optional<GeometryIr> geometry(String identifier) {
        return Optional.ofNullable(geometries.get(identifier));
    }

    public boolean isEmpty() {
        return geometries.isEmpty() && items.isEmpty() && attachables.isEmpty();
    }
}
