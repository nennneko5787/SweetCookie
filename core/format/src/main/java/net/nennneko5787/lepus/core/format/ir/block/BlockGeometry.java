package net.nennneko5787.lepus.core.format.ir.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.geometry.BoneIr;
import net.nennneko5787.lepus.core.format.ir.geometry.CubeFace;
import net.nennneko5787.lepus.core.format.ir.geometry.CubeIr;
import net.nennneko5787.lepus.core.format.ir.geometry.FaceUv;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.lepus.core.format.ir.geometry.ItemDisplay;
import net.nennneko5787.lepus.core.format.json.CanonicalJson;
import net.nennneko5787.lepus.core.format.json.JsonArray;
import net.nennneko5787.lepus.core.format.json.JsonNumber;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonString;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.Vec3f;

/**
 * Path A: a Bedrock block geometry, transpiled to a Java block model. SC-150 §5.
 *
 * <p>Bedrock models are bones holding boxes with per-face UV; Java block models are a flat
 * {@code elements[]} list of axis-aligned boxes with per-face UV. Where the two agree — and for the
 * models packs actually write for blocks, they agree almost entirely — a transpile buys vanilla's
 * chunk meshing, ambient occlusion and lighting for the cost of writing some JSON.
 *
 * <p><b>Rejection is a first-class outcome.</b> {@link #transpilable} is §5.1's predicate and
 * {@link #modelJson} answers empty for anything it rejects; the caller draws a unit cube and says so
 * (§5.2). Nothing here throws, and nothing here half-transpiles.
 *
 * <p>Minecraft-free, deliberately: this is the arithmetic that can be got wrong — the centre-to-corner
 * offset, the UV divisor, inflate, mirror — and it is all testable in milliseconds without a client.
 */
@SpecImpl({"SC-150#minecraft:geometry", "SC-150#minecraft:unit_cube"})
public final class BlockGeometry {

    /**
     * The rotations a Java element can express: one axis, one of these angles.
     *
     * <p>Not a limitation this project chose. {@code elements[].rotation.angle} is validated against
     * exactly this set by Minecraft's own model loader, and a model carrying anything else is
     * rejected at load with no block drawn at all — which is why the predicate checks it here rather
     * than emitting it and hoping.
     */
    private static final float[] JAVA_ANGLES = {-45.0f, -22.5f, 0.0f, 22.5f, 45.0f};

    /** Keys that mean "this is not a box". §5.1 rules 1 and 2. */
    private static final Set<String> NOT_A_BOX = Set.of("poly_mesh", "texture_meshes");

    private BlockGeometry() {
    }

    /**
     * §5.1's predicate: can this whole model become a Java block model?
     *
     * <p>Whole-model, never per-bone. Half a model drawn by one renderer and half by another is
     * worse than all of it drawn by the slower one.
     */
    public static boolean transpilable(GeometryIr geometry) {
        if (geometry.unknown().names().stream().anyMatch(NOT_A_BOX::contains)) {
            return false;
        }
        for (BoneIr bone : geometry.bones()) {
            // `binding` is Molang bone reparenting, evaluated per frame against a baked model.
            if (bone.bind().isPresent()) {
                return false;
            }
            if (chainOf(geometry, bone).isEmpty()) {
                return false;
            }
            if (bone.unknown().names().stream().anyMatch(NOT_A_BOX::contains)) {
                return false;
            }
            for (CubeIr cube : bone.cubes()) {
                if (!javaRotation(cube.rotation()).isPresent() && cube.isRotated()) {
                    return false;
                }
                if (cube.unknown().names().stream().anyMatch(NOT_A_BOX::contains)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The Java block model for a transpilable geometry.
     *
     * @param textureRefs the Java texture reference for each material instance the model names,
     *                    e.g. {@code {"*": "lepus:block/16_0", "up": …}}. A face naming an
     *                    instance that is absent here falls back to {@code "*"}, and a model whose
     *                    faces resolve to nothing at all is rejected — a model with no texture is
     *                    not something to draw, it is something to report.
     * @return the model JSON, or empty when §5.1 rejects the model or it has nothing to draw
     */
    public static Optional<String> modelJson(GeometryIr geometry, Map<String, String> textureRefs) {
        return modelJson(geometry, textureRefs, BlockTransform.NONE);
    }

    /**
     * The Java block model for a transpilable geometry, with {@code minecraft:transformation}
     * applied.
     *
     * <p>Baked rather than carried, because a Java block model has nowhere to carry it: the
     * {@code display} block is for how an item is held, not for where a block sits. So the boxes
     * move, and the model that comes out is already facing the right way — which is what makes one
     * geometry able to serve four directions through four permutations.
     *
     * <p>A rotation that is not a quarter turn is <b>ignored rather than refused</b>. Refusing would
     * send the block to the cube fallback and lose its shape entirely; ignoring loses only the
     * turn, which is strictly less wrong and is what happened before any of this existed.
     */
    public static Optional<String> modelJson(GeometryIr geometry, Map<String, String> textureRefs,
            BlockTransform transform) {
        if (!transpilable(geometry)) {
            return Optional.empty();
        }
        TRANSFORM.set(transform == null ? BlockTransform.NONE : transform);
        try {
            return build(geometry, textureRefs);
        } finally {
            TRANSFORM.remove();
        }
    }

    /**
     * The transformation being applied, handed to {@link #element} without threading it through
     * four signatures that have nothing else to do with it.
     *
     * <p>Set and cleared around one call, so nothing outside it can observe one.
     */
    private static final ThreadLocal<BlockTransform> TRANSFORM =
            ThreadLocal.withInitial(() -> BlockTransform.NONE);

    private static Optional<String> build(GeometryIr geometry, Map<String, String> textureRefs) {
        // Instance name -> "#0", "#1"… in first-use order, so the same model always names its
        // textures the same way and a golden does not churn on an unrelated edit.
        Map<String, String> slots = new LinkedHashMap<>();
        List<JsonValue> elements = new ArrayList<>();

        for (BoneIr bone : geometry.bones()) {
            if (bone.neverRender()) {
                // The bone exists to position its children and draws nothing itself.
                continue;
            }
            for (CubeIr cube : bone.cubes()) {
                element(geometry, bone, cube, textureRefs, slots).ifPresent(elements::add);
            }
        }
        if (elements.isEmpty() || slots.isEmpty()) {
            return Optional.empty();
        }

        Map<String, JsonValue> textures = new LinkedHashMap<>();
        slots.forEach((instance, slot) ->
                textures.put(slot.substring(1), new JsonString(textureRefs.get(instance))));
        // What breaking the block and walking on it throws off. Java does not derive it, and absent
        // it every interaction with the block puffs the missing texture.
        textures.put("particle", new JsonString(textureRefs.get(slots.keySet().iterator().next())));

        Map<String, JsonValue> model = new LinkedHashMap<>();
        // For the display transforms, not for geometry: block/block carries how a block is held,
        // dropped and shown in an inventory, and carries no elements of its own. Without it the
        // block is right in the world and wrong in every hand holding it.
        model.put("parent", new JsonString("minecraft:block/block"));
        // And what the PACK said about the same thing, which wins where it says anything. Java
        // resolves `display` one context at a time against the parent chain, so a pack that states
        // only `gui` keeps block/block's answer for the hands, the ground and the item frame.
        displayJson(geometry).ifPresent(display -> model.put("display", display));
        model.put("textures", new JsonObject(textures));
        model.put("elements", new JsonArray(elements));
        return Optional.of(CanonicalJson.pretty(new JsonObject(model)));
    }

    /**
     * The model's {@code display} block: the pack's {@code item_display_transforms}, in Java's space.
     * SC-150 §5.6.
     *
     * <p><b>Why a block model carries this at all.</b> The item form of a bound block draws this
     * same file, and everything about it is right in the world whether or not this is here — so the
     * symptom of dropping it is an icon at Java's default angle while the block itself is perfect,
     * which reads as a mirrored icon rather than as a missing feature. It is the one part of a
     * geometry whose absence is visible in exactly one place.
     *
     * <p>Three conversions, and only one of them is arithmetic:
     *
     * <ul>
     *   <li><b>rotation</b> goes through {@link #javaAngle}, the same correction the model's own
     *       turns get — the pack's angle sense, and the mirror reversing a turn about x or y.
     *   <li><b>translation</b> is a displacement rather than a position, so it takes the mirror's
     *       sign on Z and <b>not</b> the centre-to-corner offset on X. Adding 8 here would push
     *       every icon half a block sideways.
     *   <li><b>scale</b> is untouched: a mirror commutes with a scale along the axes.
     * </ul>
     *
     * <p><b>Not observed: the order the three angles compose in.</b> Java reads {@code rotation} as
     * X then Y then Z, and this assumes Bedrock does the same. Conjugating by the mirror preserves
     * whatever the order is, so a single-axis transform is right either way and only a transform
     * turning about two axes at once could show a difference.
     *
     * <p>Emitted in {@link ItemDisplay#CONTEXTS} order rather than the file's, so two packs that
     * declare the same transforms in a different order produce the same bytes.
     */
    private static Optional<JsonValue> displayJson(GeometryIr geometry) {
        Map<String, JsonValue> display = new LinkedHashMap<>();
        for (String context : ItemDisplay.CONTEXTS) {
            ItemDisplay transform = geometry.itemDisplay().get(context);
            if (transform == null) {
                continue;
            }
            Map<String, JsonValue> node = new LinkedHashMap<>();
            node.put("rotation", vec(
                    javaAngle("x", transform.rotation().x()),
                    javaAngle("y", transform.rotation().y()),
                    javaAngle("z", transform.rotation().z())));
            node.put("translation", vec(transform.translation().x(), transform.translation().y(),
                    -transform.translation().z()));
            node.put("scale", vec(transform.scale().x(), transform.scale().y(),
                    transform.scale().z()));
            display.put(context, new JsonObject(node));
        }
        return display.isEmpty() ? Optional.empty() : Optional.of(new JsonObject(display));
    }

    /** One cube as one Java element, or empty when none of its faces resolve to a texture. */
    private static Optional<JsonValue> element(GeometryIr geometry, BoneIr bone, CubeIr cube,
            Map<String, String> textureRefs, Map<String, String> slots) {
        float inflate = cube.inflate() + bone.inflate();
        Vec3f origin = cube.origin();
        Vec3f size = cube.size();

        // EVERYTHING BELOW IS IN BEDROCK'S OWN SPACE, until toJava at the end. SC-110 §6.1 says the
        // IR keeps Bedrock's axes and the conversion happens once; doing the turns in a half-
        // converted space is what made the face table and the point rotation disagree about
        // handedness, and a disagreement there is invisible on a symmetric model.
        float x1 = origin.x() - inflate;
        float y1 = origin.y() - inflate;
        float z1 = origin.z() - inflate;
        float x2 = origin.x() + size.x() + inflate;
        float y2 = origin.y() + size.y() + inflate;
        float z2 = origin.z() + size.z() + inflate;

        // The bone chain's quarter turns, baked in: Java has no bones, so the box is moved to where
        // they put it and each face is emitted under the direction it ends up facing.
        List<TurnAt> chain = chainOf(geometry, bone).orElse(List.of());
        for (TurnAt step : chain) {
            float[] rotated = turnBox(step.turn(), step.pivot(), x1, y1, z1, x2, y2, z2);
            x1 = rotated[0];
            y1 = rotated[1];
            z1 = rotated[2];
            x2 = rotated[3];
            y2 = rotated[4];
            z2 = rotated[5];
        }

        // minecraft:transformation, about the block's own centre. Scale then rotate then translate,
        // which is the order a transform is read in and the order that makes the pivot mean the
        // block's middle rather than wherever a translation had just moved it to.
        BlockTransform transform = TRANSFORM.get();
        Optional<Turn> modelTurn = quarterTurn(transform.rotation());
        if (!transform.isIdentity()) {
            float[] scaled = scaleBox(transform.scale(), x1, y1, z1, x2, y2, z2);
            x1 = scaled[0];
            y1 = scaled[1];
            z1 = scaled[2];
            x2 = scaled[3];
            y2 = scaled[4];
            z2 = scaled[5];
            if (modelTurn.isPresent()) {
                float[] turned = turnBox(modelTurn.get(), CENTRE, x1, y1, z1, x2, y2, z2);
                x1 = turned[0];
                y1 = turned[1];
                z1 = turned[2];
                x2 = turned[3];
                y2 = turned[4];
                z2 = turned[5];
            }
            x1 += transform.translation().x();
            x2 += transform.translation().x();
            y1 += transform.translation().y();
            y2 += transform.translation().y();
            z1 += transform.translation().z();
            z2 += transform.translation().z();
        }

        Map<String, JsonValue> faces = new LinkedHashMap<>();
        boolean mirror = cube.mirror() || bone.mirror();
        for (CubeFace face : CubeFace.values()) {
            CubeFace placed = face;
            int uvTurn = 0;
            for (TurnAt step : chain) {
                // The spin is accumulated against the face as it stands when that turn happens,
                // which is why this is inside the loop and not a sum over the original face.
                uvTurn = Math.floorMod(uvTurn + uvRotation(step.turn(), placed), 360);
                placed = turnFace(step.turn(), placed);
            }
            if (modelTurn.isPresent()) {
                uvTurn = Math.floorMod(uvTurn + uvRotation(modelTurn.get(), placed), 360);
                placed = turnFace(modelTurn.get(), placed);
            }
            // The mirror lands here: the face is emitted under the direction it points to in JAVA,
            // and the conversion's own spin is added to whatever the turns accumulated. The pack's
            // `mirror` is left alone - it is the only thing that may flip a texture.
            CubeFace at = toJava(placed);
            int spin = Math.floorMod(uvTurn + toJavaSpin(placed), 360);
            cube.face(face)
                    .flatMap(uv -> faceJson(geometry, uv, mirror, spin, textureRefs, slots))
                    .ifPresent(json -> faces.put(at.declared(), json));
        }
        if (faces.isEmpty()) {
            // A cube declaring no UV at all gets an empty face map at parse time rather than a
            // guessed rectangle (SC-180 §3.2). An element with no faces draws nothing, so it is
            // dropped here rather than emitted as an invisible box.
            return Optional.empty();
        }

        float[] java = toJava(x1, y1, z1, x2, y2, z2);
        Map<String, JsonValue> element = new LinkedHashMap<>();
        element.put("from", vec(java[0], java[1], java[2]));
        element.put("to", vec(java[3], java[4], java[5]));
        javaRotation(cube.rotation()).ifPresent(rotation -> {
            Map<String, JsonValue> node = new LinkedHashMap<>();
            // A pivot is a point in the same space as the box it turns, so it goes through the same
            // conversion - LITERALLY the same one, called rather than restated. It used to be
            // restated, and it kept the X mirror after the mirror had moved to Z: every rotated
            // cube then turned about a point mirrored across the block on one axis and off by a
            // whole block on the other.
            float[] pivot = toJava(cube.pivot().x(), cube.pivot().y(), cube.pivot().z(),
                    cube.pivot().x(), cube.pivot().y(), cube.pivot().z());
            node.put("origin", vec(pivot[0], pivot[1], pivot[2]));
            node.put("axis", new JsonString(rotation.axis()));
            node.put("angle", JsonNumber.of(javaAngle(rotation.axis(), rotation.angle())));
            element.put("rotation", new JsonObject(node));
        });
        element.put("faces", new JsonObject(faces));
        return Optional.of(new JsonObject(element));
    }

    /**
     * One face.
     *
     * <p>The UV divisor lands here and nowhere else. The IR keeps Bedrock's texel units undivided on
     * purpose (SC-180 §3.4) — packs get {@code texture_width} wrong, and folding a wrong divisor
     * into the data makes the mistake unrecoverable — so this is where the model's own numbers turn
     * into Java's 0..16-over-the-whole-texture space.
     */
    private static Optional<JsonValue> faceJson(GeometryIr geometry, FaceUv uv, boolean mirror,
            int uvRotation, Map<String, String> textureRefs, Map<String, String> slots) {
        String instance = uv.materialInstance().orElse("*");
        if (!textureRefs.containsKey(instance)) {
            instance = "*";
        }
        if (!textureRefs.containsKey(instance)) {
            return Optional.empty();
        }
        String slot = slots.get(instance);
        if (slot == null) {
            slot = "#" + slots.size();
            slots.put(instance, slot);
        }

        float width = Math.max(1, geometry.textureWidth());
        float height = Math.max(1, geometry.textureHeight());
        float u1 = uv.uv().x() / width * 16.0f;
        float v1 = uv.uv().y() / height * 16.0f;
        float u2 = (uv.uv().x() + uv.uvSize().x()) / width * 16.0f;
        float v2 = (uv.uv().y() + uv.uvSize().y()) / height * 16.0f;
        if (mirror) {
            // Java has no mirror flag; it reads u1 > u2 as a flipped face, which is the same thing.
            float swap = u1;
            u1 = u2;
            u2 = swap;
        }

        Map<String, JsonValue> face = new LinkedHashMap<>();
        face.put("uv", new JsonArray(List.of(JsonNumber.of(round(u1)), JsonNumber.of(round(v1)),
                JsonNumber.of(round(u2)), JsonNumber.of(round(v2)))));
        if (uvRotation != 0) {
            // Only the face whose normal lies along the turn's axis spins in texture space. The
            // others keep their orientation - they are simply emitted under a different direction.
            face.put("rotation", JsonNumber.of(uvRotation));
        }
        face.put("texture", new JsonString(slot));
        return Optional.of(new JsonObject(face));
    }

    /**
     * A bone rotation that maps boxes onto boxes: one axis, a multiple of 90°. §5.1 rule 3.
     *
     * <p>A quarter turn takes an axis-aligned box to another axis-aligned box — it permutes the axes
     * and flips some signs, and nothing else. That is the whole reason this case is transpilable
     * where a free rotation is not.
     *
     * <p><b>One axis only.</b> Two axes at once would need Bedrock's composition order, which is not
     * written down anywhere this project can check, and getting it wrong gives a model that is right
     * in outline and wrong in orientation — the failure that is hardest to attribute.
     *
     * @return the turn, or empty when the rotation is not one
     */
    private static Optional<Turn> quarterTurn(Vec3f rotation) {
        int axes = 0;
        axes += rotation.x() != 0 ? 1 : 0;
        axes += rotation.y() != 0 ? 1 : 0;
        axes += rotation.z() != 0 ? 1 : 0;
        if (axes != 1) {
            return Optional.empty();
        }
        Axis axis = rotation.x() != 0 ? Axis.X : rotation.y() != 0 ? Axis.Y : Axis.Z;
        float degrees = rotation.x() != 0 ? rotation.x()
                : rotation.y() != 0 ? rotation.y() : rotation.z();
        // Modulo into 0..270. A pack writing -90 and one writing 270 mean the same turn.
        double quarters = BEDROCK_TURN_SENSE * degrees / 90.0;
        if (Math.abs(quarters - Math.rint(quarters)) > 1.0e-4) {
            return Optional.empty();
        }
        int steps = Math.floorMod((int) Math.rint(quarters), 4);
        return steps == 0 ? Optional.empty() : Optional.of(new Turn(axis, steps));
    }

    private enum Axis { X, Y, Z }

    /**
     * A rotation of {@code steps} quarter turns about {@code axis}.
     *
     * <p><b>The direction is asserted, not verified.</b> A right-handed turn is assumed, matching the
     * coordinate mapping §5.3 already uses. Two quarter turns — the common case, and the one the
     * first real add-on needed — give the same answer whichever way round it is, so a wrong
     * assumption here shows up only at 90° and 270°. One constant, {@link #HANDEDNESS}, flips it.
     */
    private record Turn(Axis axis, int steps) {
    }

    /** A turn and the pivot it happens about. One bone's contribution to its cubes' placement. */
    private record TurnAt(Turn turn, Vec3f pivot) {
    }

    /** How deep a bone chain may be before this gives up and calls it a cycle. */
    private static final int MAX_BONE_DEPTH = 64;

    /**
     * Every turn that applies to a bone's cubes, innermost first.
     *
     * <p><b>A bone inherits its parents' rotations.</b> Missing this is the difference between
     * working and not: the first real add-on to need any of it put {@code rotation} on a
     * <i>childless</i> {@code root} bone and the cubes in its child, which is the ordinary way to
     * orient a Bedrock model. Reading each bone's own rotation alone would have transpiled that
     * model into one that never turns, and it would have looked like a rotation bug rather than a
     * missing feature.
     *
     * <p>Ordered self-first, then outward, which is what a parent chain means: a child turns about
     * its own pivot, and then the whole of it turns about its parent's.
     *
     * @return the chain, or empty when any bone in it turns by something that is not a quarter turn
     */
    private static Optional<List<TurnAt>> chainOf(GeometryIr geometry, BoneIr bone) {
        List<TurnAt> chain = new ArrayList<>();
        BoneIr at = bone;
        for (int depth = 0; depth < MAX_BONE_DEPTH; depth++) {
            if (!at.rotation().isZero()) {
                Optional<Turn> turn = quarterTurn(at.rotation());
                if (turn.isEmpty()) {
                    return Optional.empty();
                }
                chain.add(new TurnAt(turn.get(), at.pivot()));
            }
            Optional<String> parent = at.parent();
            if (parent.isEmpty()) {
                return Optional.of(chain);
            }
            String name = parent.get();
            Optional<BoneIr> next = geometry.bones().stream()
                    .filter(candidate -> candidate.name().equals(name))
                    .findFirst();
            if (next.isEmpty()) {
                // A parent nobody declares. Bedrock's own files do this and it is SC-180 §3.2's
                // problem, not this one's: the chain simply stops here rather than the model being
                // refused over a name.
                return Optional.of(chain);
            }
            at = next.get();
        }
        // Deeper than any real model, so this is a cycle. Refusing is the safe answer; following it
        // would be an infinite loop inside a resource reload.
        return Optional.empty();
    }

    /**
     * +1 for a right-handed turn: the sense every turn in this file is composed in.
     *
     * <p><b>Not the place to correct a model that turns the wrong way.</b> {@link #quarter} and
     * {@link #uvRotation} are written out against this value rather than derived from it, so
     * flipping it alone would move each box one way and relabel its faces the other — a model right
     * in outline with its textures on the wrong sides. What a pack's angles mean is
     * {@link #BEDROCK_TURN_SENSE}, and that is the one to reach for.
     */
    private static final int HANDEDNESS = 1;

    /**
     * Which way a Bedrock rotation angle turns, in {@link #HANDEDNESS}'s terms: <b>the other way</b>.
     *
     * <p><b>Observed.</b> A real pack orients one geometry four ways through four permutations —
     * {@code north} 0°, {@code south} 180°, {@code west} +90°, {@code east} −90° — and with this at
     * +1 the two blocks placed along the east-west line came out holding each other's rotation while
     * north and south were right. Half turns are the same either way round, so a wrong sense can
     * only ever show up at 90° and 270°, and it showing up at exactly those two is the signature.
     *
     * <p>This is one number about Bedrock's convention, not about the mirror: the mirror decides
     * which way the un-rotated model faces (§5.3), and that was already right — north and south
     * would have been wrong too, otherwise.
     *
     * <p>Applied to every angle a pack writes, in {@link #quarterTurn} for the turns that are baked
     * into boxes and in {@link #javaAngle} for the ones Java can express itself. Both, or a cube
     * rotated 45° and a bone rotated 90° would disagree about which way round the model goes.
     */
    private static final int BEDROCK_TURN_SENSE = -1;

    /**
     * A cube rotation's angle as Java wants it: Java's space, Java's sense.
     *
     * <p>Two corrections, and they are about different things. {@link #BEDROCK_TURN_SENSE} says what
     * the pack's number means. The negation on {@code x} and {@code y} is §5.3's mirror, which
     * reverses a turn about either of the two axes it does not preserve and leaves one about
     * {@code z} alone.
     *
     * <p>{@link #JAVA_ANGLES} is symmetric about zero, so whatever comes out of this is still an
     * angle Minecraft's model loader will accept.
     */
    private static float javaAngle(String axis, float angle) {
        float rightHanded = BEDROCK_TURN_SENSE * angle;
        return "z".equals(axis) ? rightHanded : -rightHanded;
    }

    /** A cube rotation Java can express, or empty when it cannot. §5.1 rule 4. */
    private static Optional<Rotation> javaRotation(Vec3f rotation) {
        if (rotation.isZero()) {
            return Optional.empty();
        }
        int axes = 0;
        axes += rotation.x() != 0 ? 1 : 0;
        axes += rotation.y() != 0 ? 1 : 0;
        axes += rotation.z() != 0 ? 1 : 0;
        if (axes != 1) {
            return Optional.empty();
        }
        String axis = rotation.x() != 0 ? "x" : rotation.y() != 0 ? "y" : "z";
        float angle = rotation.x() != 0 ? rotation.x()
                : rotation.y() != 0 ? rotation.y() : rotation.z();
        for (float allowed : JAVA_ANGLES) {
            if (allowed == angle) {
                return Optional.of(new Rotation(axis, angle));
            }
        }
        return Optional.empty();
    }

    private record Rotation(String axis, float angle) {
    }

    /**
     * The box after {@code turn} about {@code pivot}, as {@code {minX, minY, minZ, maxX, maxY, maxZ}}.
     *
     * <p>Both corners are transformed and the result re-ordered, because a quarter turn moves the
     * minimum corner to what may be the maximum one. Taking the transformed corners as min and max
     * unchanged would give a box with a negative extent — which the record's constructor would then
     * collapse to a point, and the cube would silently vanish.
     */
    private static float[] turnBox(Turn turn, Vec3f pivot, float x1, float y1, float z1,
            float x2, float y2, float z2) {
        // Pivot and box are both in Bedrock's space now, so the pivot is used as written. It used
        // to carry a +8 here to meet a box that had already been converted; that is gone with the
        // conversion, and leaving it would shift every rotated cube by a whole half block.
        float[] a = turnPoint(turn, pivot.x(), pivot.y(), pivot.z(), x1, y1, z1);
        float[] b = turnPoint(turn, pivot.x(), pivot.y(), pivot.z(), x2, y2, z2);
        return new float[] {
                Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
    }

    /**
     * The block's own centre, <b>in Bedrock's space</b>: X and Z run from the centre there, so it is
     * zero on both, and Y runs from the floor, so it is eight.
     */
    private static final Vec3f CENTRE = new Vec3f(0.0f, 8.0f, 0.0f);

    /** The box scaled about the block's centre. An AABB scaled about a point is still an AABB. */
    private static float[] scaleBox(Vec3f scale, float x1, float y1, float z1,
            float x2, float y2, float z2) {
        float cx = CENTRE.x();
        float cy = CENTRE.y();
        float cz = CENTRE.z();
        return new float[] {
                cx + (x1 - cx) * scale.x(), cy + (y1 - cy) * scale.y(), cz + (z1 - cz) * scale.z(),
                cx + (x2 - cx) * scale.x(), cy + (y2 - cy) * scale.y(), cz + (z2 - cz) * scale.z()};
    }

    /**
     * Bedrock's space to Java's, once, for one box. SC-110 §6.1.
     *
     * <p>Two different things happen to the two horizontal axes and that is the whole point:
     *
     * <ul>
     *   <li><b>Z is mirrored</b> as well as offset. The two editions disagree about handedness — the
     *       normative documents said so before any of this was written — and the symptom of ignoring
     *       it is a model that is correct in every dimension and inside out, which every symmetric
     *       test in this file was blind to.
     *   <li><b>X is only offset</b>, because Bedrock measures it from the block's centre and Java
     *       from its corner.
     * </ul>
     *
     * <p><b>Which axis, and how it was settled.</b> A mirror on X and a mirror on Z both cure the
     * inside-out look; they differ by a half turn about Y, which is to say by which way the model
     * faces. X was tried first and left the model facing backwards, and that showed up in two
     * places at once: a block placed along the north-south line came out the wrong way round, and
     * the model in the hand — which is the <em>same file</em> as the icon in the hotbar, seen from a
     * different angle — still looked wrong after the icon looked right. The hand sees a front-to-back
     * swap that a three-quarter icon view hides.
     *
     * <p>Flipping between the two is this method, {@link #toJava(CubeFace)} and the angle
     * conjugation in {@code element}, and nothing else.
     *
     * <p>The corners are re-ordered afterwards: mirroring sends the smaller X to the larger one, and
     * a box whose {@code from} is past its {@code to} draws nothing at all.
     */
    private static float[] toJava(float x1, float y1, float z1, float x2, float y2, float z2) {
        float jz1 = 8.0f - z1;
        float jz2 = 8.0f - z2;
        return new float[] {
                x1 + 8.0f, y1, Math.min(jz1, jz2),
                x2 + 8.0f, y2, Math.max(jz1, jz2)};
    }

    /**
     * Where a Bedrock face ends up pointing once the mirror is applied: north and south trade
     * places, and the other four keep their names.
     */
    private static CubeFace toJava(CubeFace face) {
        return switch (face) {
            case NORTH -> CubeFace.SOUTH;
            case SOUTH -> CubeFace.NORTH;
            default -> face;
        };
    }

    /**
     * The spin the axis conversion puts on a face, in degrees.
     *
     * <p><b>The conversion never flips a texture.</b> Both engines draw a face whose UV rectangle
     * has positive extent un-mirrored, so a conversion between them can only ever <em>rotate</em> a
     * face — if it flipped one, that face would render as its own mirror image, which is a thing
     * neither engine does to anybody's model.
     *
     * <p>An earlier version of this method flipped {@code north}, {@code south}, {@code up} and
     * {@code down} and not {@code east}/{@code west}. That cannot be right whatever the two engines'
     * conventions turn out to be: the four side faces' U directions circulate around the box in one
     * consistent sense in both engines, so a conversion either reverses all four or none. Two of
     * four is not a possible answer.
     *
     * <p>With the mirror on Z, no face needs a spin either: a Z mirror differs from an X mirror by a
     * half turn about Y, and that half turn is exactly the 180° the X version had to put on the top
     * and bottom. The two corrections cancel, which is a small piece of evidence for Z being the
     * right axis — the conversion comes out as a pure relabelling.
     *
     * <p><b>Not yet observed.</b> A texture whose top face is obviously asymmetric settles it, and
     * the correction is one number here.
     */
    private static int toJavaSpin(CubeFace face) {
        return 0;
    }

    /** One point, turned about the pivot. Quarter turns need no trigonometry. */
    private static float[] turnPoint(Turn turn, float px, float py, float pz,
            float x, float y, float z) {
        float dx = x - px;
        float dy = y - py;
        float dz = z - pz;
        for (int step = 0; step < turn.steps(); step++) {
            float ox = dx;
            float oy = dy;
            float oz = dz;
            switch (turn.axis()) {
                case X -> {
                    dy = -HANDEDNESS * oz;
                    dz = HANDEDNESS * oy;
                }
                case Y -> {
                    dx = HANDEDNESS * oz;
                    dz = -HANDEDNESS * ox;
                }
                case Z -> {
                    dx = -HANDEDNESS * oy;
                    dy = HANDEDNESS * ox;
                }
            }
        }
        return new float[] {px + dx, py + dy, pz + dz};
    }

    /** Where a face ends up pointing after the turn. */
    private static CubeFace turnFace(Turn turn, CubeFace face) {
        CubeFace moved = face;
        for (int step = 0; step < turn.steps(); step++) {
            moved = quarter(turn.axis(), moved);
        }
        return moved;
    }

    /**
     * One quarter turn's effect on a face direction.
     *
     * <p>The cycles are the same rotation {@link #turnPoint} applies, written out: a face's normal is
     * a point, and a quarter turn sends each of the four faces around the axis to the next one. The
     * two faces ON the axis do not move — they spin in place, which is {@link #uvRotation}'s job.
     */
    private static CubeFace quarter(Axis axis, CubeFace face) {
        // Java's directions: north is -Z, south is +Z, east is +X, west is -X, up is +Y.
        return switch (axis) {
            case Y -> switch (face) {
                // (x, z) -> (z, -x): north(-Z) -> west(-X), west(-X) -> south(+Z), and so on round.
                case NORTH -> CubeFace.WEST;
                case WEST -> CubeFace.SOUTH;
                case SOUTH -> CubeFace.EAST;
                case EAST -> CubeFace.NORTH;
                case UP, DOWN -> face;
            };
            case X -> switch (face) {
                // (y, z) -> (-z, y): up(+Y) -> south(+Z)? no - up -> north, going round the X axis.
                case UP -> CubeFace.SOUTH;
                case SOUTH -> CubeFace.DOWN;
                case DOWN -> CubeFace.NORTH;
                case NORTH -> CubeFace.UP;
                case EAST, WEST -> face;
            };
            case Z -> switch (face) {
                // (x, y) -> (-y, x), read off turnPoint: east(+X) -> up(+Y) -> west(-X) ->
                // down(-Y) -> east. This ran the OTHER WAY round until a design review derived it
                // from turnPoint rather than from the neighbouring arms - a bone with a 90 degree
                // Z rotation moved its box one way and relabelled its faces the other, so every
                // texture landed on the wrong side of a correctly shaped model. Invisible in every
                // test here, because they all turn by 180 degrees, where the two cancel.
                case EAST -> CubeFace.UP;
                case UP -> CubeFace.WEST;
                case WEST -> CubeFace.DOWN;
                case DOWN -> CubeFace.EAST;
                case NORTH, SOUTH -> face;
            };
        };
    }

    /**
     * How far a face's texture spins, in degrees, as a result of the turn.
     *
     * <p>Only the two faces whose normal lies along the turn's axis spin. Every other face is carried
     * round to a new direction with its texture still the right way up, and Java reads its {@code uv}
     * in the new direction's own frame — so it needs no correction.
     */
    private static int uvRotation(Turn turn, CubeFace face) {
        boolean onAxis = switch (turn.axis()) {
            case X -> face == CubeFace.EAST || face == CubeFace.WEST;
            case Y -> face == CubeFace.UP || face == CubeFace.DOWN;
            case Z -> face == CubeFace.NORTH || face == CubeFace.SOUTH;
        };
        return onAxis ? turn.steps() * 90 : 0;
    }

    private static JsonValue vec(float x, float y, float z) {
        return new JsonArray(List.of(
                JsonNumber.of(round(x)), JsonNumber.of(round(y)), JsonNumber.of(round(z))));
    }

    /**
     * To three decimals.
     *
     * <p>A UV divisor of 6 or 12 — both real, both in packs — produces repeating decimals, and a
     * model file carrying {@code 2.6666666} is one a reviewer cannot compare against the pack it
     * came from. Three decimals is finer than a texel on a 4096-wide texture.
     */
    private static double round(float value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    /** Names every material instance the model's faces actually use. For texture resolution. */
    public static Set<String> instancesUsed(GeometryIr geometry) {
        Set<String> used = new LinkedHashSet<>();
        for (BoneIr bone : geometry.bones()) {
            for (CubeIr cube : bone.cubes()) {
                for (CubeFace face : CubeFace.values()) {
                    used.add(cube.face(face).flatMap(FaceUv::materialInstance).orElse("*"));
                }
            }
        }
        return used;
    }

}
