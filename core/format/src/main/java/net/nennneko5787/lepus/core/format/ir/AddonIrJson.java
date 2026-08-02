package net.nennneko5787.lepus.core.format.ir;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.block.BlockBox;
import net.nennneko5787.lepus.core.format.ir.block.BlockGeometry;
import net.nennneko5787.lepus.core.format.ir.block.BlockModels;
import net.nennneko5787.lepus.core.format.ir.block.BlockPhysics;
import net.nennneko5787.lepus.core.format.ir.block.BlockTransform;
import net.nennneko5787.lepus.core.format.ir.geometry.BoneIr;
import net.nennneko5787.lepus.core.format.ir.geometry.CubeFace;
import net.nennneko5787.lepus.core.format.ir.geometry.CubeIr;
import net.nennneko5787.lepus.core.format.ir.geometry.FaceUv;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.lepus.core.format.ir.geometry.LocatorIr;
import net.nennneko5787.lepus.core.format.ir.item.ItemProfile;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonArray;
import net.nennneko5787.lepus.core.format.json.JsonBool;
import net.nennneko5787.lepus.core.format.json.JsonNull;
import net.nennneko5787.lepus.core.format.json.JsonNumber;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonString;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.pack.LoadedAddonJson;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import net.nennneko5787.lepus.core.format.value.Vec2f;
import net.nennneko5787.lepus.core.format.value.Vec3f;

/**
 * Renders {@link AddonIr} as JSON, for conformance goldens. SC-110 §11 family 3.
 *
 * <p>Everything {@code LoadedAddonJson} says about paths and ordering applies here too: paths are
 * rewritten and separator-normalised so a golden is machine-independent, and nothing is sorted that
 * carries meaning.
 *
 * <p>The {@code unknown} bag is rendered by <b>name only</b>, not by value. Its purpose in a golden
 * is to answer "did the parser start ignoring something it used to read", and the names answer that;
 * the values would drag whole subtrees of unparsed JSON into every golden and make the interesting
 * lines unfindable.
 */
@SpecImpl("SC-110")
public final class AddonIrJson {

    private AddonIrJson() {
    }

    public static JsonObject of(AddonIr addon, UnaryOperator<String> rewritePath) {
        List<JsonValue> packs = new ArrayList<>();
        for (PackIr pack : addon.packs()) {
            Map<String, JsonValue> node =
                    new LinkedHashMap<>(LoadedAddonJson.pack(pack.source(), rewritePath).members());
            node.put("behavior", behavior(pack.behavior(), pack.resource()));
            node.put("resource", resource(pack.resource()));
            packs.add(new JsonObject(node));
        }
        Map<String, JsonValue> root = new LinkedHashMap<>();
        root.put("packs", new JsonArray(packs));
        root.put("diagnostics", LoadedAddonJson.diagnostics(addon.diagnostics(), rewritePath));
        return new JsonObject(root);
    }

    private static JsonValue behavior(BehaviorIr behavior, ResourceIr resource) {
        Map<String, JsonValue> blocks = new LinkedHashMap<>();
        behavior.blocks().forEach((id, block) -> blocks.put(id.toString(), block(block, resource)));
        Map<String, JsonValue> items = new LinkedHashMap<>();
        behavior.items().forEach((id, item) -> items.put(id.toString(), item(item)));
        Map<String, JsonValue> node = new LinkedHashMap<>();
        node.put("blocks", new JsonObject(blocks));
        if (!items.isEmpty()) {
            // Absent rather than empty, so every golden written before items were projected still
            // describes the same IR.
            node.put("items", new JsonObject(items));
        }
        return new JsonObject(node);
    }

    /**
     * One item: what the pack declared, and what this build makes of it.
     *
     * <p>The {@code profile} node is the item's counterpart of a block's {@code model} — the
     * <b>conclusion</b> rather than the input, so a golden over component names alone cannot stay
     * green through a unit mix-up or a component read from the wrong nesting level. Absent fields
     * are omitted rather than written as null: "the pack said nothing" is the thing that has to
     * survive to the runtime, and a line vanishing from a golden is as visible as one changing.
     */
    private static JsonValue item(net.nennneko5787.lepus.core.format.ir.item.ItemDefIr item) {
        Map<String, JsonValue> node = new LinkedHashMap<>();
        node.put("identifier", string(item.identifier().toString()));
        if (!item.menuCategory().isEmpty()) {
            node.put("menuCategory", string(item.menuCategory()));
        }
        node.put("inCreative", JsonBool.of(item.inCreative()));
        node.put("components", new JsonArray(item.components().keySet().stream()
                .map(id -> string(id.toString())).toList()));

        ItemProfile profile = ItemProfile.of(item.components());
        Map<String, JsonValue> resolved = new LinkedHashMap<>();
        profile.maxStackSize().ifPresent(v -> resolved.put("maxStackSize", JsonNumber.of(v)));
        profile.maxDurability().ifPresent(v -> resolved.put("maxDurability", JsonNumber.of(v)));
        profile.wearableSlot().ifPresent(v -> resolved.put("wearableSlot", string(v)));
        profile.javaEquipmentSlot().ifPresent(v -> resolved.put("javaEquipmentSlot", string(v)));
        profile.protection().ifPresent(v -> resolved.put("protection", JsonNumber.of(v)));
        profile.enchantValue().ifPresent(v -> resolved.put("enchantValue", JsonNumber.of(v)));
        profile.glint().ifPresent(v -> resolved.put("glint", JsonBool.of(v)));
        profile.nameKey().ifPresent(v -> resolved.put("nameKey", string(v)));
        node.put("profile", new JsonObject(resolved));
        unknown(node, item.unknown());
        return new JsonObject(node);
    }

    private static JsonValue block(net.nennneko5787.lepus.core.format.ir.block.BlockDefIr b,
            ResourceIr resource) {
        Map<String, JsonValue> node = new LinkedHashMap<>();
        node.put("identifier", string(b.identifier().toString()));
        if (!b.menuCategory().isEmpty()) {
            node.put("menuCategory", string(b.menuCategory()));
        }
        node.put("states", new JsonArray(b.schema().states().stream()
                .map(state -> (JsonValue) new JsonObject(Map.of(
                        "name", string(state.name().toString()),
                        "kind", string(state.kind().name()),
                        "values", new JsonArray(state.values().stream()
                                .map(AddonIrJson::string).toList()))))
                .toList()));
        node.put("stateCount", JsonNumber.of(b.schema().size()));
        node.put("components", new JsonArray(b.components().keySet().stream()
                .map(id -> string(id.toString())).toList()));
        node.put("permutations", new JsonArray(b.permutations().stream()
                .map(p -> (JsonValue) new JsonObject(Map.of(
                        "condition", string(p.condition().source()),
                        "components", new JsonArray(p.components().keySet().stream()
                                .map(id -> string(id.toString())).toList()))))
                .toList()));
        // The resolved component set per state index: the thing SC-150 §3 actually computes, and
        // the only part of a block a golden can check without a world.
        node.put("resolved", new JsonArray(b.resolveAll().stream()
                .map(resolved -> (JsonValue) new JsonArray(resolved.keySet().stream()
                        .map(id -> string(id.toString())).toList()))
                .toList()));
        // The VALUES those components resolve to, which `resolved` cannot show: a golden over
        // component names alone stays green through a sign error in an origin or a unit mix-up in a
        // size. `resolved` still answers the other question — which permutation matched.
        node.put("physics", new JsonArray(b.resolveAll().stream()
                .map(resolved -> physics(BlockPhysics.of(resolved))).toList()));
        // The Java model each state transpiles to, when Path A takes it (SC-150 §5). Textures are
        // named by the BEDROCK TEXTURE KEY the pack wrote rather than by the file the runtime will
        // emit: a golden that carried generated file names would churn on a slot allocation and say
        // nothing about the conversion, which is the part that can be wrong.
        node.put("model", new JsonArray(b.resolveAll().stream()
                .map(resolved -> transpiled(resolved, resource)).toList()));
        unknown(node, b.unknown());
        return new JsonObject(node);
    }

    private static JsonValue transpiled(Map<BedrockId, JsonValue> components, ResourceIr resource) {
        return BlockModels.geometryOf(components)
                .flatMap(resource::geometry)
                .flatMap(geometry -> BlockGeometry.modelJson(geometry, BlockModels.materialsOf(components).textureKeys(),
                        BlockTransform.of(components).orElse(BlockTransform.NONE)))
                // Re-parsed rather than embedded as a string: a golden holding an escaped JSON
                // document on one line is one nobody can read a diff of.
                .map(Json::parse)
                .orElse(JsonNull.INSTANCE);
    }

    /**
     * One state's resolved physics, <b>with everything at its Bedrock default left out</b>.
     *
     * <p>A block has 32 components and most states set none of them; spelling out six defaults per
     * state turns a 4-state block into 24 lines that say nothing and a 32-state one into 192. What
     * remains is what the pack actually asked for, which is the thing a reviewer is looking for.
     *
     * <p>Omission does not hide a regression: a value that wrongly falls back to its default
     * <em>disappears</em> from the golden, and a line vanishing is as visible in a diff as a line
     * changing.
     */
    private static JsonValue physics(BlockPhysics physics) {
        Map<String, JsonValue> node = new LinkedHashMap<>();
        BlockPhysics defaults = BlockPhysics.DEFAULT;
        if (!physics.destroySeconds().equals(defaults.destroySeconds())) {
            node.put("destroySeconds", physics.destroySeconds()
                    .<JsonValue>map(AddonIrJson::number).orElse(JsonNull.INSTANCE));
        }
        if (!physics.explosionResistance().equals(defaults.explosionResistance())) {
            node.put("explosionResistance", physics.explosionResistance()
                    .<JsonValue>map(AddonIrJson::number).orElse(JsonNull.INSTANCE));
        }
        if (physics.lightEmission() != defaults.lightEmission()) {
            node.put("lightEmission", JsonNumber.of(physics.lightEmission()));
        }
        if (physics.friction() != defaults.friction()) {
            node.put("friction", number(physics.friction()));
        }
        if (!physics.collision().equals(defaults.collision())) {
            node.put("collision", box(physics.collision()));
        }
        if (!physics.selection().equals(defaults.selection())) {
            node.put("selection", box(physics.selection()));
        }
        return new JsonObject(node);
    }

    /** A box as {@code [minX, minY, minZ, maxX, maxY, maxZ]} in pixels, or null when there is none. */
    private static JsonValue box(java.util.Optional<BlockBox> box) {
        return box.<JsonValue>map(b -> new JsonArray(List.of(
                        number(b.minX()), number(b.minY()), number(b.minZ()),
                        number(b.maxX()), number(b.maxY()), number(b.maxZ()))))
                .orElse(JsonNull.INSTANCE);
    }

    /**
     * A {@code float} as a JSON number, spelled as a float rather than as the double it widens to.
     *
     * <p>{@code 0.4f} widened is {@code 0.4000000059604645}, and a golden full of those is one
     * nobody reads. Going through the float's own shortest round-tripping form gives {@code 0.4},
     * which is what the pack wrote and what the reviewer is checking against.
     */
    private static JsonNumber number(float value) {
        return JsonNumber.of(Double.parseDouble(Float.toString(value)));
    }

    private static JsonValue resource(ResourceIr resource) {
        Map<String, JsonValue> geometries = new LinkedHashMap<>();
        resource.geometries().forEach((id, geometry) -> geometries.put(id, geometry(geometry)));
        return new JsonObject(Map.of("geometries", new JsonObject(geometries)));
    }

    private static JsonValue geometry(GeometryIr geometry) {
        Map<String, JsonValue> node = new LinkedHashMap<>();
        node.put("identifier", string(geometry.identifier()));
        geometry.parent().ifPresent(parent -> node.put("parent", string(parent)));
        node.put("sourceFamily", string(geometry.sourceFamily().declared()));
        node.put("textureWidth", JsonNumber.of(geometry.textureWidth()));
        node.put("textureHeight", JsonNumber.of(geometry.textureHeight()));
        geometry.visibleBounds().ifPresent(bounds -> node.put("visibleBounds", new JsonObject(Map.of(
                "width", JsonNumber.of(bounds.width()),
                "height", JsonNumber.of(bounds.height()),
                "offset", vec3(bounds.offset())))));
        node.put("bones", new JsonArray(geometry.bones().stream()
                .map(AddonIrJson::bone).toList()));
        if (!geometry.itemDisplay().isEmpty()) {
            // Absent rather than empty when the model states none, so that every golden written
            // before this existed still describes the same IR.
            Map<String, JsonValue> display = new LinkedHashMap<>();
            geometry.itemDisplay().forEach((context, transform) -> display.put(context,
                    new JsonObject(Map.of(
                            "rotation", vec3(transform.rotation()),
                            "translation", vec3(transform.translation()),
                            "scale", vec3(transform.scale())))));
            node.put("itemDisplay", new JsonObject(display));
        }
        // The declared and effective versions are the whole point of the sniffing case: a golden
        // that showed only the result could not tell "parsed as modern" from "declared modern".
        node.put("declaredVersion", string(geometry.provenance().declaredVersion()));
        node.put("effectiveVersion", string(geometry.provenance().effectiveVersion()));
        node.put("lossy", JsonBool.of(geometry.provenance().lossy()));
        unknown(node, geometry.unknown());
        return new JsonObject(node);
    }

    private static JsonValue bone(BoneIr bone) {
        Map<String, JsonValue> node = new LinkedHashMap<>();
        node.put("name", string(bone.name()));
        bone.parent().ifPresent(parent -> node.put("parent", string(parent)));
        node.put("pivot", vec3(bone.pivot()));
        node.put("rotation", vec3(bone.rotation()));
        bone.bind().ifPresent(bind -> node.put("binding", string(bind)));
        if (bone.mirror()) {
            node.put("mirror", JsonBool.TRUE);
        }
        if (bone.inflate() != 0f) {
            node.put("inflate", JsonNumber.of(bone.inflate()));
        }
        if (bone.neverRender()) {
            node.put("neverRender", JsonBool.TRUE);
        }
        node.put("cubes", new JsonArray(bone.cubes().stream().map(AddonIrJson::cube).toList()));
        if (!bone.locators().isEmpty()) {
            node.put("locators",
                    new JsonArray(bone.locators().stream().map(AddonIrJson::locator).toList()));
        }
        unknown(node, bone.unknown());
        return new JsonObject(node);
    }

    private static JsonValue cube(CubeIr cube) {
        Map<String, JsonValue> node = new LinkedHashMap<>();
        node.put("origin", vec3(cube.origin()));
        node.put("size", vec3(cube.size()));
        if (!cube.pivot().isZero()) {
            node.put("pivot", vec3(cube.pivot()));
        }
        if (cube.isRotated()) {
            node.put("rotation", vec3(cube.rotation()));
        }
        if (cube.inflate() != 0f) {
            node.put("inflate", JsonNumber.of(cube.inflate()));
        }
        if (cube.mirror()) {
            node.put("mirror", JsonBool.TRUE);
        }
        Map<String, JsonValue> uv = new LinkedHashMap<>();
        for (CubeFace face : CubeFace.values()) {
            cube.face(face).ifPresent(value -> uv.put(face.declared(), faceUv(value)));
        }
        node.put("uv", new JsonObject(uv));
        unknown(node, cube.unknown());
        return new JsonObject(node);
    }

    private static JsonValue faceUv(FaceUv face) {
        Map<String, JsonValue> node = new LinkedHashMap<>();
        node.put("uv", vec2(face.uv()));
        node.put("uvSize", vec2(face.uvSize()));
        face.materialInstance().ifPresent(m -> node.put("materialInstance", string(m)));
        return new JsonObject(node);
    }

    private static JsonValue locator(LocatorIr locator) {
        Map<String, JsonValue> node = new LinkedHashMap<>();
        node.put("name", string(locator.name()));
        node.put("offset", vec3(locator.offset()));
        if (!locator.rotation().isZero()) {
            node.put("rotation", vec3(locator.rotation()));
        }
        if (locator.ignoreInheritedScale()) {
            node.put("ignoreInheritedScale", JsonBool.TRUE);
        }
        return new JsonObject(node);
    }

    private static void unknown(Map<String, JsonValue> node, UnknownData unknown) {
        if (unknown.isEmpty()) {
            return;
        }
        node.put("unknown", new JsonArray(
                unknown.names().stream().sorted().map(AddonIrJson::string).toList()));
    }

    private static JsonValue vec3(Vec3f v) {
        return new JsonArray(List.of(
                JsonNumber.of(v.x()), JsonNumber.of(v.y()), JsonNumber.of(v.z())));
    }

    private static JsonValue vec2(Vec2f v) {
        return new JsonArray(List.of(JsonNumber.of(v.x()), JsonNumber.of(v.y())));
    }

    private static JsonValue string(String value) {
        return new JsonString(value);
    }
}
