package net.nennneko5787.lepus.core.format.ir.geometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.ir.IrDiagnostics;
import net.nennneko5787.lepus.core.format.ir.ParseContext;
import net.nennneko5787.lepus.core.format.ir.ParserRegistry;
import net.nennneko5787.lepus.core.format.ir.UnknownData;
import net.nennneko5787.lepus.core.format.json.JsonArray;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.Provenance;
import net.nennneko5787.lepus.core.format.value.Vec2f;
import net.nennneko5787.lepus.core.format.value.Vec3f;

/**
 * Reads a {@code .geo.json} file in either family into one IR. SC-180 §3, SC-110 §3.
 *
 * <p>This is the project's canonical demonstration of SC-110 §3.1 rule 3. The two families are
 * structurally incompatible, both appear in the same vanilla pack, and packs declare the wrong one
 * constantly — so the shape decides and the declaration is only reported.
 *
 * <p>Bone and cube parsing is shared between the families rather than duplicated, because they
 * differ in exactly three places: where the identifier lives, what the texture-size keys are called,
 * and whether {@code uv} may be an object. The last one is not even a family difference in practice —
 * the modern family accepts box UV too — so the cube parser handles both shapes and the family only
 * decides what to do when a file uses the shape it should not have.
 */
@SpecImpl({"SC-180", "SC-180#geometry/family_1_8", "SC-180#geometry/family_modern"})
public final class GeometryFiles {

    /** The member the modern family keys its model array under. */
    private static final String MODERN_ROOT = "minecraft:geometry";

    private static final Set<String> MODEL_KEYS_MODERN = Set.of("description", "bones", "cape");
    private static final Set<String> DESCRIPTION_KEYS = Set.of(
            "identifier", "texture_width", "texture_height",
            "visible_bounds_width", "visible_bounds_height", "visible_bounds_offset");
    private static final Set<String> MODEL_KEYS_LEGACY = Set.of(
            "bones", "texturewidth", "textureheight",
            "visible_bounds_width", "visible_bounds_height", "visible_bounds_offset");
    private static final Set<String> BONE_KEYS = Set.of(
            "name", "parent", "pivot", "rotation", "bind_pose_rotation", "binding",
            "mirror", "inflate", "never_render", "cubes", "locators", "poly_mesh",
            "texture_meshes", "debug", "render_group_id");
    private static final Set<String> CUBE_KEYS = Set.of(
            "origin", "size", "pivot", "rotation", "uv", "inflate", "mirror");

    private static final ParserRegistry<List<GeometryIr>> REGISTRY =
            new ParserRegistry<List<GeometryIr>>("geometry")
                    .register(GeometryFamily.LEGACY_1_8.version(), GeometryFiles::parseLegacy)
                    .register(GeometryFamily.MODERN.version(), GeometryFiles::parseModern)
                    // Structure decides. A file holding `minecraft:geometry` is modern whatever it
                    // declares; one holding `geometry.*` keys is legacy whatever it declares.
                    .sniffer(root -> root.has(MODERN_ROOT)
                            ? Optional.of(GeometryFamily.MODERN.version())
                            : Optional.empty())
                    .sniffer(root -> root.keys().stream().anyMatch(GeometryFiles::isLegacyModelKey)
                            ? Optional.of(GeometryFamily.LEGACY_1_8.version())
                            : Optional.empty());

    private GeometryFiles() {
    }

    /** Every model in one file, in declaration order. Empty when nothing could be read. */
    public static List<GeometryIr> parse(JsonObject root, Provenance file, Diagnostics into) {
        return REGISTRY.parse(root, "format_version", file, into).orElse(List.of());
    }

    /** The registered ladder, so a test can assert both families are reachable. */
    public static List<net.nennneko5787.lepus.core.format.value.BedrockVersion> ladder() {
        return REGISTRY.registeredVersions();
    }

    private static boolean isLegacyModelKey(String key) {
        return key.startsWith("geometry.");
    }

    // ── The modern family ────────────────────────────────────────────────────────────────────

    private static Optional<List<GeometryIr>> parseModern(JsonObject root, ParseContext ctx) {
        Optional<JsonArray> models = root.getArray(MODERN_ROOT);
        if (models.isEmpty()) {
            ctx.at(MODERN_ROOT).reportMissing(MODERN_ROOT);
            return Optional.of(List.of());
        }
        List<GeometryIr> out = new ArrayList<>();
        ParseContext arrayAt = ctx.at(MODERN_ROOT);
        for (int i = 0; i < models.get().size(); i++) {
            ParseContext at = arrayAt.at(i);
            Optional<JsonObject> model = models.get().values().get(i).asObject();
            if (model.isEmpty()) {
                at.report(IrDiagnostics.FIELD_MALFORMED, MODERN_ROOT, "element is not an object");
                continue;
            }
            parseModernModel(model.get(), at).ifPresent(out::add);
        }
        return Optional.of(out);
    }

    private static Optional<GeometryIr> parseModernModel(JsonObject model, ParseContext ctx) {
        Optional<JsonObject> description = model.getObject("description");
        if (description.isEmpty()) {
            ctx.at("description").reportMissing("description");
            return Optional.empty();
        }
        JsonObject desc = description.get();
        ParseContext descAt = ctx.at("description");

        String identifier = desc.getString("identifier").orElse("");
        if (identifier.isBlank()) {
            descAt.at("identifier").reportMissing("identifier");
            return Optional.empty();
        }

        return Optional.of(new GeometryIr(
                stripParent(identifier),
                parentOf(identifier),
                GeometryFamily.MODERN,
                descAt.intValue(desc, "texture_width", GeometryIr.DEFAULT_TEXTURE_SIZE),
                descAt.intValue(desc, "texture_height", GeometryIr.DEFAULT_TEXTURE_SIZE),
                visibleBounds(desc, descAt),
                parseBones(model, ctx),
                ctx.provenance(),
                UnknownData.of(model, MODEL_KEYS_MODERN)));
    }

    // ── The 1.8.0 family ─────────────────────────────────────────────────────────────────────

    private static Optional<List<GeometryIr>> parseLegacy(JsonObject root, ParseContext ctx) {
        List<GeometryIr> out = new ArrayList<>();
        for (String key : root.keys()) {
            if (!isLegacyModelKey(key)) {
                continue;
            }
            ParseContext at = ctx.at(key);
            Optional<JsonObject> model = root.getObject(key);
            if (model.isEmpty()) {
                at.report(IrDiagnostics.FIELD_MALFORMED, key, "not an object");
                continue;
            }
            JsonObject m = model.get();
            out.add(new GeometryIr(
                    stripParent(key),
                    parentOf(key),
                    GeometryFamily.LEGACY_1_8,
                    at.intValue(m, "texturewidth", GeometryIr.DEFAULT_TEXTURE_SIZE),
                    at.intValue(m, "textureheight", GeometryIr.DEFAULT_TEXTURE_SIZE),
                    visibleBounds(m, at),
                    parseBones(m, at),
                    at.provenance(),
                    UnknownData.of(m, MODEL_KEYS_LEGACY)));
        }
        if (out.isEmpty()) {
            ctx.reportMissing("geometry.*");
        }
        return Optional.of(out);
    }

    // ── Shared ───────────────────────────────────────────────────────────────────────────────

    /**
     * Splits {@code geometry.a:geometry.b} on the FIRST colon.
     *
     * <p>Both halves contain dots and the parent half contains no further colon in any observed
     * file, but splitting on the last colon would mangle a three-level chain the day one appears.
     */
    private static String stripParent(String identifier) {
        int colon = identifier.indexOf(':');
        return colon < 0 ? identifier : identifier.substring(0, colon);
    }

    private static Optional<String> parentOf(String identifier) {
        int colon = identifier.indexOf(':');
        return colon < 0 || colon + 1 >= identifier.length()
                ? Optional.empty()
                : Optional.of(identifier.substring(colon + 1));
    }

    private static Optional<GeometryIr.VisibleBounds> visibleBounds(
            JsonObject source, ParseContext ctx) {
        boolean declared = source.has("visible_bounds_width")
                || source.has("visible_bounds_height")
                || source.has("visible_bounds_offset");
        if (!declared) {
            return Optional.empty();
        }
        return Optional.of(new GeometryIr.VisibleBounds(
                ctx.floatValue(source, "visible_bounds_width", 0f),
                ctx.floatValue(source, "visible_bounds_height", 0f),
                ctx.vec3(source, "visible_bounds_offset", Vec3f.ZERO)));
    }

    private static List<BoneIr> parseBones(JsonObject model, ParseContext ctx) {
        Optional<JsonArray> bones = model.getArray("bones");
        if (bones.isEmpty()) {
            // A model with no bones is legal and common: it is how a pack declares a placeholder,
            // and how an inheriting model says "the parent's bones, unchanged".
            return List.of();
        }
        List<BoneIr> out = new ArrayList<>();
        ParseContext arrayAt = ctx.at("bones");
        for (int i = 0; i < bones.get().size(); i++) {
            ParseContext at = arrayAt.at(i);
            Optional<JsonObject> bone = bones.get().values().get(i).asObject();
            if (bone.isEmpty()) {
                at.report(IrDiagnostics.FIELD_MALFORMED, "bones", "element is not an object");
                continue;
            }
            parseBone(bone.get(), at).ifPresent(out::add);
        }
        return out;
    }

    private static Optional<BoneIr> parseBone(JsonObject bone, ParseContext ctx) {
        String name = bone.getString("name").orElse("");
        if (name.isBlank()) {
            // Without a name nothing can reference it, parent it, or animate it. Skipping one bone
            // costs less than refusing the model.
            ctx.at("name").reportMissing("name");
            return Optional.empty();
        }
        return Optional.of(new BoneIr(
                name,
                bone.getString("parent").filter(p -> !p.isBlank()),
                ctx.vec3(bone, "pivot", Vec3f.ZERO),
                ctx.vec3(bone, "rotation", Vec3f.ZERO),
                bone.getString("binding").filter(b -> !b.isBlank()),
                ctx.boolValue(bone, "mirror", false),
                ctx.floatValue(bone, "inflate", 0f),
                ctx.boolValue(bone, "never_render", false),
                parseCubes(bone, ctx),
                parseLocators(bone, ctx),
                UnknownData.of(bone, BONE_KEYS)));
    }

    private static List<CubeIr> parseCubes(JsonObject bone, ParseContext ctx) {
        Optional<JsonArray> cubes = bone.getArray("cubes");
        if (cubes.isEmpty()) {
            return List.of();
        }
        List<CubeIr> out = new ArrayList<>();
        ParseContext arrayAt = ctx.at("cubes");
        for (int i = 0; i < cubes.get().size(); i++) {
            ParseContext at = arrayAt.at(i);
            Optional<JsonObject> cube = cubes.get().values().get(i).asObject();
            if (cube.isEmpty()) {
                at.report(IrDiagnostics.FIELD_MALFORMED, "cubes", "element is not an object");
                continue;
            }
            out.add(parseCube(cube.get(), at));
        }
        return out;
    }

    private static CubeIr parseCube(JsonObject cube, ParseContext ctx) {
        Vec3f size = ctx.vec3(cube, "size", Vec3f.ZERO);
        return new CubeIr(
                ctx.vec3(cube, "origin", Vec3f.ZERO),
                size,
                ctx.vec3(cube, "pivot", Vec3f.ZERO),
                ctx.vec3(cube, "rotation", Vec3f.ZERO),
                ctx.floatValue(cube, "inflate", 0f),
                ctx.boolValue(cube, "mirror", false),
                parseUv(cube, size, ctx),
                UnknownData.of(cube, CUBE_KEYS));
    }

    /**
     * Reads either UV shape.
     *
     * <p>Box UV is not exclusive to the {@code 1.8.0} family — modern files use it constantly — so
     * the shape of the value decides rather than the family. A cube with no {@code uv} at all gets
     * an empty face map rather than a guess, because a guessed rectangle renders as the wrong part
     * of the texture and looks deliberate.
     */
    private static Map<CubeFace, FaceUv> parseUv(JsonObject cube, Vec3f size, ParseContext ctx) {
        Optional<JsonValue> uv = cube.get("uv");
        if (uv.isEmpty()) {
            return Map.of();
        }
        ParseContext at = ctx.at("uv");
        Optional<JsonArray> box = uv.get().asArray();
        if (box.isPresent()) {
            List<Float> parts = box.get().floats();
            if (parts.size() < 2) {
                at.report(IrDiagnostics.FIELD_MALFORMED, "uv", parts.size() + " components");
                return Map.of();
            }
            return BoxUv.expand(Vec2f.of(parts), size);
        }
        Optional<JsonObject> perFace = uv.get().asObject();
        if (perFace.isEmpty()) {
            at.report(IrDiagnostics.FIELD_MALFORMED, "uv", uv.get().typeName());
            return Map.of();
        }
        Map<CubeFace, FaceUv> faces = new LinkedHashMap<>();
        for (String key : perFace.get().keys()) {
            Optional<CubeFace> face = CubeFace.parse(key);
            if (face.isEmpty()) {
                at.at(key).report(IrDiagnostics.FIELD_MALFORMED, key, "not a cube face");
                continue;
            }
            Optional<JsonObject> entry = perFace.get().getObject(key);
            if (entry.isEmpty()) {
                at.at(key).report(IrDiagnostics.FIELD_MALFORMED, key, "not an object");
                continue;
            }
            ParseContext faceAt = at.at(key);
            faces.put(face.get(), new FaceUv(
                    faceAt.vec2(entry.get(), "uv", Vec2f.ZERO),
                    faceAt.vec2(entry.get(), "uv_size", Vec2f.ZERO),
                    entry.get().getString("material_instance").filter(m -> !m.isBlank())));
        }
        return faces;
    }

    /**
     * Reads both locator spellings.
     *
     * <p>Sorted by name. Bedrock writes locators as a JSON object, and although this project's JSON
     * reader preserves member order, a golden that depended on the order an author happened to type
     * would churn on an edit that changed nothing (SC-110 §10 asks for determinism, not for
     * faithfulness to typing order).
     */
    private static List<LocatorIr> parseLocators(JsonObject bone, ParseContext ctx) {
        Optional<JsonObject> locators = bone.getObject("locators");
        if (locators.isEmpty()) {
            return List.of();
        }
        List<LocatorIr> out = new ArrayList<>();
        ParseContext at = ctx.at("locators");
        for (String name : locators.get().keys()) {
            JsonValue value = locators.get().members().get(name);
            Optional<JsonArray> shortForm = value.asArray();
            if (shortForm.isPresent()) {
                out.add(LocatorIr.of(name, Vec3f.of(shortForm.get().floats())));
                continue;
            }
            Optional<JsonObject> longForm = value.asObject();
            if (longForm.isEmpty()) {
                at.at(name).report(IrDiagnostics.FIELD_MALFORMED, name, value.typeName());
                continue;
            }
            ParseContext locatorAt = at.at(name);
            out.add(new LocatorIr(
                    name,
                    locatorAt.vec3(longForm.get(), "offset", Vec3f.ZERO),
                    locatorAt.vec3(longForm.get(), "rotation", Vec3f.ZERO),
                    locatorAt.boolValue(longForm.get(), "ignore_inherited_scale", false)));
        }
        out.sort(Comparator.comparing(LocatorIr::name));
        return out;
    }
}
