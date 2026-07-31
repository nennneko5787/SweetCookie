package net.nennneko5787.sweetcookie.core.format.ir;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.ir.geometry.BoneIr;
import net.nennneko5787.sweetcookie.core.format.ir.geometry.CubeFace;
import net.nennneko5787.sweetcookie.core.format.ir.geometry.CubeIr;
import net.nennneko5787.sweetcookie.core.format.ir.geometry.FaceUv;
import net.nennneko5787.sweetcookie.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.sweetcookie.core.format.ir.geometry.LocatorIr;
import net.nennneko5787.sweetcookie.core.format.json.JsonArray;
import net.nennneko5787.sweetcookie.core.format.json.JsonBool;
import net.nennneko5787.sweetcookie.core.format.json.JsonNumber;
import net.nennneko5787.sweetcookie.core.format.json.JsonObject;
import net.nennneko5787.sweetcookie.core.format.json.JsonString;
import net.nennneko5787.sweetcookie.core.format.json.JsonValue;
import net.nennneko5787.sweetcookie.core.format.pack.LoadedAddonJson;
import net.nennneko5787.sweetcookie.core.format.value.Vec2f;
import net.nennneko5787.sweetcookie.core.format.value.Vec3f;

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
            node.put("resource", resource(pack.resource()));
            packs.add(new JsonObject(node));
        }
        Map<String, JsonValue> root = new LinkedHashMap<>();
        root.put("packs", new JsonArray(packs));
        root.put("diagnostics", LoadedAddonJson.diagnostics(addon.diagnostics(), rewritePath));
        return new JsonObject(root);
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
