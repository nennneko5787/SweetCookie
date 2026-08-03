package net.nennneko5787.lepus.core.testkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.AddonIr;
import net.nennneko5787.lepus.core.format.ir.IrLoader;
import net.nennneko5787.lepus.core.format.ir.PackIr;
import net.nennneko5787.lepus.core.format.ir.animation.AnimationIr;
import net.nennneko5787.lepus.core.format.ir.attachable.AttachableIr;
import net.nennneko5787.lepus.core.format.ir.block.BlockDefIr;
import net.nennneko5787.lepus.core.format.ir.block.BlockGeometry;
import net.nennneko5787.lepus.core.format.ir.block.BlockModels;
import net.nennneko5787.lepus.core.format.ir.block.BlockPhysics;
import net.nennneko5787.lepus.core.format.ir.block.BlockTransform;
import net.nennneko5787.lepus.core.format.ir.block.FlipbookTextures;
import net.nennneko5787.lepus.core.format.ir.block.LegacyBlockIndex;
import net.nennneko5787.lepus.core.format.ir.block.TerrainTextures;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.lepus.core.format.ir.item.ItemDefIr;
import net.nennneko5787.lepus.core.format.ir.item.ItemProfile;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.pack.AddonLoader;
import net.nennneko5787.lepus.core.format.pack.LoadedAddon;
import net.nennneko5787.lepus.core.format.render.AnimationSampler;
import net.nennneko5787.lepus.core.format.render.AttachableContext;
import net.nennneko5787.lepus.core.format.render.AttachablePoser;
import net.nennneko5787.lepus.core.format.render.Mat4f;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import net.nennneko5787.lepus.core.molang.MolangExpr;

/**
 * What real add-ons use, and how much of it this build reads. {@code spec/process.md} §1.
 *
 * <p><b>Why this exists.</b> Every defect found in the first week of running real add-ons was the
 * same shape: a pack used a spelling, a file or a format the readers did not know about, and the
 * symptom appeared far from the cause — a missing texture that was a legacy {@code blocks.json}, a
 * stretched texture that was an animation, a dark lamp that was a 0-to-1 light value. Each was found
 * by a person looking at a block and reporting it, one at a time.
 *
 * <p>All of them were measurable in advance. This measures them: point it at a folder of installed
 * add-ons and it reports which component identifiers appear, which of those anything reads, which
 * textures and geometries resolve, and what is sitting in the unknown bag. It needs no Minecraft and
 * no world.
 *
 * <p><b>It asks the readers what they read.</b> {@code BlockPhysics.READS} and friends are the same
 * constants the parsing uses, so this cannot claim support that is not there — a survey with its own
 * list of known components would drift, and would drift towards flattering the build.
 *
 * <p>Add-ons are never committed to this repository (constitution rule 10), so this takes a path.
 */
@SpecImpl("SC-110")
public final class AddonSurvey {

    /** One component identifier and how many definitions used it. */
    public record Usage(String id, int count, boolean read) {
    }

    /**
     * What one run found.
     *
     * @param blocksWithoutTexture blocks whose materials resolve to no file — the offline form of
     *                             {@code SCE-2032}, and the check that would have caught the first
     *                             three misdiagnoses in this project's history
     */
    public record Report(int packs, int blocks, int items, List<Usage> blockComponents,
            List<Usage> itemComponents, List<String> blocksWithoutTexture,
            List<String> geometryPathA, Map<String, String> geometryRejected,
            int animatedTextures, List<String> unknownKeys, List<String> notes,
            int attachables, List<String> attachablesWithoutGeometry) {
    }

    private AddonSurvey() {
    }

    /**
     * Where one attachable's bones actually land, once posed. {@code spec/process.md} §1.
     *
     * <p><b>For the question "is that part of the model missing, or is it somewhere else".</b> On
     * screen those look identical, and answering it by launching a client and turning the camera is
     * slow and inconclusive. Here every bone's cubes go through the same bind-pose maths the
     * renderer uses and come back as numbers.
     *
     * @param path a folder of add-ons, and the geometry identifier to report on
     */
    public static List<String> poseReport(Path root, String geometryId) throws IOException {
        return poseReport(root, geometryId, null);
    }

    /**
     * As above, with an animation applied at t=0.
     *
     * <p><b>Separates two failures that look identical on screen.</b> "The leg swings the wrong
     * way" is either the sampler applying the angle backwards or the renderer mirroring it
     * afterwards, and the difference is invisible in a frame. Here the answer is in Bedrock's own
     * coordinates, before any conversion — so if the leg is where the pack meant it, the fault is
     * downstream, and if it is not, it never left this layer.
     */
    public static List<String> poseReport(Path root, String geometryId, String animationName)
            throws IOException {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> entries = Files.list(root)) {
            entries.forEach(sources::add);
        }
        AddonIr ir = IrLoader.parse(AddonLoader.load(sources));
        GeometryIr geometry = null;
        for (PackIr pack : ir.packs()) {
            GeometryIr found = pack.resource().geometries().get(geometryId);
            if (found != null) {
                geometry = found;
            }
        }
        if (geometry == null) {
            return List.of("no geometry named " + geometryId);
        }

        Map<String, net.nennneko5787.lepus.core.format.render.Mat4f> pose;
        if (animationName == null) {
            pose = net.nennneko5787.lepus.core.format.render.BoneMatrices.bindPose(geometry);
        } else {
            net.nennneko5787.lepus.core.format.ir.animation.AnimationIr animation = null;
            for (PackIr pack : ir.packs()) {
                var found = pack.resource().animation(animationName);
                if (found.isPresent()) {
                    animation = found.get();
                }
            }
            if (animation == null) {
                return List.of("no animation named " + animationName);
            }
            var sampler =
                    new net.nennneko5787.lepus.core.format.render.AnimationSampler(animation);
            var poser = new net.nennneko5787.lepus.core.format.render.AttachablePoser(
                    geometry, List.of(Map.entry(sampler, Optional.<String>empty())), List.of());
            pose = poser.at(new net.nennneko5787.lepus.core.format.render.Playback(),
                    net.nennneko5787.lepus.core.molang.MolangContext.standalone());
        }
        List<String> lines = new ArrayList<>();
        lines.add(geometryId + ": " + geometry.bones().size() + " bones, "
                + geometry.cubeCount() + " cubes, " + pose.size() + " posed"
                + (animationName == null ? " (bind pose)" : " (" + animationName + " at t=0)"));
        lines.addAll(extents(geometry, pose));
        return lines;
    }

    /**
     * Every bone's cubes, as a box in Bedrock's own coordinates.
     *
     * <p>Ranges rather than a matrix, because the question this answers is always "is that part of
     * the model missing, or is it somewhere else" — and a matrix does not say where a limb ended up.
     */
    private static List<String> extents(GeometryIr geometry, Map<String, Mat4f> pose) {
        List<String> lines = new ArrayList<>();
        for (var bone : geometry.bones()) {
            var matrix = pose.get(bone.name());
            if (matrix == null) {
                lines.add("  " + bone.name() + ": NO POSE (a cycle in its parent chain)");
                continue;
            }
            if (bone.cubes().isEmpty()) {
                continue;
            }
            float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (var cube : bone.cubes()) {
                for (int corner = 0; corner < 8; corner++) {
                    float x = cube.origin().x() + ((corner & 1) == 0 ? 0 : cube.size().x());
                    float y = cube.origin().y() + ((corner & 2) == 0 ? 0 : cube.size().y());
                    float z = cube.origin().z() + ((corner & 4) == 0 ? 0 : cube.size().z());
                    float[] at = matrix.transform(x, y, z);
                    for (int axis = 0; axis < 3; axis++) {
                        lo[axis] = Math.min(lo[axis], at[axis]);
                        hi[axis] = Math.max(hi[axis], at[axis]);
                    }
                }
            }
            lines.add(String.format(java.util.Locale.ROOT,
                    "  %-12s x %7.2f..%7.2f  y %7.2f..%7.2f  z %7.2f..%7.2f  (%d cubes)",
                    bone.name(), lo[0], hi[0], lo[1], hi[1], lo[2], hi[2], bone.cubes().size()));
        }
        return lines;
    }

    /**
     * Where a real attachable's bones land <b>in one view</b>. {@code spec/process.md} §1.
     *
     * <p><b>The measurement {@link #poseReport} could not make.</b> That one applies a single named
     * animation; a view plays the whole of {@code scripts.animate}, conditions evaluated against the
     * view. The difference is not academic — the bug that put a piggybacking character two blocks
     * off the player's shoulder lived entirely in how several animations compose, so a tool that
     * could only pose one of them at a time was blind to it by construction.
     *
     * <p>This builds the same {@link AttachablePoser} the binder builds and evaluates it against the
     * same {@link AttachableContext} the renderer passes, so what it prints is what a frame computes
     * — up to the Molang queries about the world, which read zero here as they do there.
     *
     * @param view {@code first} or {@code third}, deciding {@code c.is_first_person}
     */
    public static List<String> attachableReport(Path root, String identifier, String view)
            throws IOException {
        return attachableReport(root, identifier, view, "main");
    }

    /**
     * As above, for one hand.
     *
     * <p><b>The slot is not decoration.</b> A pack gates its first-person animation on
     * {@code v.main_hand = c.item_slot == 'main_hand'}, so the same attachable in the other hand
     * plays a different set — and an investigation that can only ask about one hand cannot tell a
     * rule that is wrong from an item that was in the other hand.
     */
    public static List<String> attachableReport(Path root, String identifier, String view,
            String slot) throws IOException {
        return attachableReport(root, identifier, view, slot, "");
    }

    /** As below, with nothing held out. */
    public static List<String> attachableReport(Path root, String identifier, String view,
            String slot, String doing) throws IOException {
        return attachableReport(root, identifier, view, slot, doing, "");
    }

    /**
     * As above, with the wearer doing something. SC-180 §5.
     *
     * <p><b>An animation controller answers questions about the wearer and nothing else.</b> An
     * instrument that could only ask about a player standing still could only ever report the
     * initial state, which is the state whose animation the corpus most often does not define — so
     * it would print "the controller draws nothing" and be right for the wrong reason.
     *
     * @param doing comma-separated: {@code sneaking}, {@code in_water}, {@code swimming},
     *              {@code gliding}, {@code sleeping}, {@code on_fire}
     * @param skip  short names of {@code scripts.animate} entries to leave out, comma-separated.
     *              <b>Composition is additive and later entries read what earlier ones left</b>
     *              (SC-180 §4.1), so no entry has a contribution of its own to print — the honest
     *              measurement of "what does this one do" is the pose with it against the pose
     *              without it. That is what this exists for
     */
    public static List<String> attachableReport(Path root, String identifier, String view,
            String slot, String doing, String skip) throws IOException {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> entries = Files.list(root)) {
            entries.forEach(sources::add);
        }
        AddonIr ir = IrLoader.parse(AddonLoader.load(sources));
        BedrockId id = BedrockId.parse(identifier);
        AttachableIr attachable = null;
        for (PackIr pack : ir.packs()) {
            var found = pack.resource().attachable(id);
            if (found.isPresent()) {
                attachable = found.get();
            }
        }
        if (attachable == null) {
            return List.of("no attachable for " + identifier);
        }
        String geometryId = attachable.defaultGeometry().orElse(null);
        GeometryIr geometry = null;
        for (PackIr pack : ir.packs()) {
            GeometryIr found = pack.resource().geometries().get(geometryId);
            if (found != null) {
                geometry = found;
            }
        }
        if (geometry == null) {
            return List.of(identifier + " names geometry " + geometryId + ", which resolves to none");
        }

        // Exactly the binder's resolution: every entry of scripts.animate that names something a
        // pack ships, in the pack's order, each keeping its blend expression - and a name may be an
        // animation OR a controller, resolved in that order, because a controller's states name the
        // same short names (SC-180 §5). An instrument that resolved only animations would report a
        // frame the renderer does not draw, which is the failure this tool has already had twice.
        Map<String, net.nennneko5787.lepus.core.format.render.Playable> byShortName =
                new java.util.LinkedHashMap<>();
        attachable.animations().forEach((shortName, named) -> {
            for (PackIr pack : ir.packs()) {
                pack.resource().animation(named).ifPresent(animation ->
                        byShortName.put(shortName, new AnimationSampler(animation)));
            }
        });
        attachable.animations().forEach((shortName, named) -> {
            if (byShortName.containsKey(shortName)) {
                return;
            }
            for (PackIr pack : ir.packs()) {
                pack.resource().controller(named).ifPresent(controller ->
                        byShortName.put(shortName,
                                new net.nennneko5787.lepus.core.format.render
                                        .AnimationControllerPlayer(controller, byShortName)));
            }
        });
        List<Map.Entry<net.nennneko5787.lepus.core.format.render.Playable, Optional<String>>>
                playing = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        java.util.Set<String> heldOut = new java.util.LinkedHashSet<>();
        for (String name : skip.split(",")) {
            if (!name.isBlank()) {
                heldOut.add(name.trim());
            }
        }
        for (AttachableIr.Play play : attachable.animate()) {
            String named = attachable.animations().get(play.name());
            var playable = byShortName.get(play.name());
            if (playable == null) {
                skipped.add(play.name() + " (" + named + ")");
                continue;
            }
            if (heldOut.contains(play.name())) {
                continue;
            }
            playing.add(Map.entry(playable, play.condition()));
        }

        boolean firstPerson = "first".equalsIgnoreCase(view);
        boolean mainHand = !"off".equalsIgnoreCase(slot);
        AttachableContext context = (firstPerson
                ? AttachableContext.firstPerson(mainHand)
                : AttachableContext.thirdPerson(mainHand))
                .doing(wearerDoing(doing));
        // The bones the WEARER drives, as a player standing still and looking straight ahead has
        // them. The renderer composes the wearer's transform outside the pack's (SC-180 §4.2)
        // rather than replacing it, so these report what a frame computes for a still player.
        //
        // IDENTITY IS NOT THE ANSWER IN FIRST PERSON. That view poses the wearer from a different
        // animation set, which writes `body` unconditionally and `waist` never (SC-180 §4.2.1) — so
        // a still player is not a still torso there. This tool fed identity to both views for a
        // while, and that is precisely why the one attachable hanging off `body` and the one hanging
        // off `waist` measured as differing only by a translation: the bone that separates them was
        // being held at rest by the instrument.
        Map<String, Mat4f> skeleton = firstPerson
                ? AttachablePoser.FIRST_PERSON_WEARER
                : Map.of("head", Mat4f.IDENTITY, "body", Mat4f.IDENTITY);
        // WHICH ENTRIES ACTUALLY RAN, not merely which exist. A conditional entry is the whole
        // difference between two views and between the two hands, and a report that lists the
        // conditions without answering them leaves the reader to evaluate Molang in their head.
        //
        // EVALUATED AFTER THE POSE, because that is when the poser evaluates them: `pre_animation`
        // has run by then and its variables are what the conditions read. Moving this ahead of the
        // pose made it print "no" for entries the pose had applied, and the extents beside it said
        // otherwise. Whichever order the poser uses, this must use the same one — it has now been
        // wrong in both directions on the same day.
        // ONE playback for the report, so the clocks and the controller's state are this holder's -
        // a fresh one, which is a player who has just picked the item up. Reading the state back
        // afterwards must not step the machine again; `currentState` is for that.
        net.nennneko5787.lepus.core.format.render.Playback playback =
                new net.nennneko5787.lepus.core.format.render.Playback();
        Map<String, Mat4f> pose = new AttachablePoser(geometry, playing, attachable.preAnimation())
                .at(playback, context, skeleton);

        List<String> lines = new ArrayList<>();
        lines.add(identifier + " as " + geometryId + ", " + (firstPerson ? "first" : "third")
                + " person, " + (mainHand ? "main" : "off") + " hand, t=0"
                + (doing.isBlank() ? "" : ", wearer " + doing)
                + (heldOut.isEmpty() ? "" : ", WITHOUT " + heldOut));
        List<String> ran = new ArrayList<>();
        for (AttachableIr.Play play : attachable.animate()) {
            String verdict = play.condition()
                    .map(when -> {
                        float value;
                        try {
                            value = MolangExpr.compile(when).evaluate(context);
                        } catch (RuntimeException unparsed) {
                            return " UNREADABLE";
                        }
                        return value != 0f ? " YES" : " no";
                    })
                    .orElse(" always");
            ran.add(play.name() + verdict);
        }
        lines.add("  plays: " + ran);
        if (!skipped.isEmpty()) {
            lines.add("  unresolved: " + skipped);
        }
        // WHICH STATE each controller is in, and whether that state draws anything. A controller
        // whose current state names an animation the attachable does not define is the normal case
        // (SC-180 §5), and on screen it is indistinguishable from a controller that did not run at
        // all - which is what this build did with every one of them until now.
        byShortName.forEach((shortName, playable) -> {
            if (playable instanceof net.nennneko5787.lepus.core.format.render
                    .AnimationControllerPlayer machine) {
                lines.add("  controller " + shortName + ": state "
                        + machine.currentState(playback));
            }
        });
        // An expression that would not compile answers zero and costs one channel, which on screen
        // is a limb resting at its bind angle - indistinguishable from an animation that simply does
        // not move it. The sampler has always recorded these; nothing asked.
        for (var entry : playing) {
            entry.getKey().unreadableExpressions()
                    .forEach(source -> lines.add("  UNREADABLE: " + source));
        }
        lines.addAll(extents(geometry, pose));
        return lines;
    }

    /**
     * Surveys every add-on directly inside {@code root}.
     *
     * <p>One level down, not recursive: that is how the installed folders are laid out, and walking
     * deeper would find a pack's own subdirectories and load them as packs.
     */
    public static Report of(Path... roots) throws IOException {
        List<Path> sources = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> entries = Files.list(root)) {
                entries.forEach(sources::add);
            }
        }
        return of(AddonLoader.load(sources));
    }

    /** Surveys an already-loaded add-on set. Split out so it can be tested without a filesystem. */
    public static Report of(LoadedAddon loaded) {
        AddonIr ir = IrLoader.parse(loaded);

        TerrainTextures terrain = merge(ir, "textures/terrain_texture.json");
        TerrainTextures items = merge(ir, "textures/item_texture.json");
        LegacyBlockIndex legacy = legacy(ir);
        FlipbookTextures flipbooks = flipbooks(ir);
        Map<String, GeometryIr> geometries = new LinkedHashMap<>();
        ir.packs().forEach(pack -> geometries.putAll(pack.resource().geometries()));

        Map<String, Integer> blockUse = new TreeMap<>();
        Map<String, Integer> itemUse = new TreeMap<>();
        Map<String, Integer> unknown = new TreeMap<>();
        List<String> untextured = new ArrayList<>();
        List<String> pathA = new ArrayList<>();
        Map<String, String> rejected = new TreeMap<>();

        int blocks = 0;
        int itemCount = 0;
        for (PackIr pack : ir.packs()) {
            for (BlockDefIr block : pack.behavior().blocks().values()) {
                blocks++;
                block.components().keySet()
                        .forEach(id -> blockUse.merge(id.toString(), 1, Integer::sum));
                block.unknown().names().forEach(name -> unknown.merge(name, 1, Integer::sum));
                surveyBlock(block, terrain, legacy, geometries, ir, untextured, pathA, rejected);
            }
            for (ItemDefIr item : pack.behavior().items().values()) {
                itemCount++;
                item.components().keySet()
                        .forEach(id -> itemUse.merge(id.toString(), 1, Integer::sum));
                item.unknown().names().forEach(name -> unknown.merge(name, 1, Integer::sum));
            }
        }

        List<String> notes = new ArrayList<>();
        if (items.byKey().isEmpty() && itemCount > 0) {
            notes.add("no item_texture.json in any pack, so item icons fall back to the identifier");
        }
        if (!legacy.isEmpty()) {
            notes.add("a legacy blocks.json is present: "
                    + legacy.texturesByBlock().size() + " textured, "
                    + legacy.soundByBlock().size() + " with sounds");
        }
        // Attachables, and whether the geometry each names is one any enabled pack ships. A
        // model that resolves to nothing is the failure this reports for: the item is held, the
        // renderer is asked for a shape, and there is none — which on screen is an item that
        // simply is not there.
        int attachableCount = 0;
        List<String> attachablesWithoutGeometry = new ArrayList<>();
        for (PackIr pack : ir.packs()) {
            for (var attachable : pack.resource().attachables().values()) {
                attachableCount++;
                boolean resolves = attachable.defaultGeometry()
                        .map(geometries::containsKey)
                        .orElse(false);
                if (!resolves) {
                    attachablesWithoutGeometry.add(attachable.identifier() + " -> "
                            + attachable.defaultGeometry().orElse("<none>"));
                }
            }
        }

        return new Report(ir.packs().size(), blocks, itemCount,
                rank(blockUse, union(BlockPhysics.READS, union(BlockModels.READS, BlockTransform.READS))),
                rank(itemUse, ItemProfile.READS),
                untextured, pathA, rejected, flipbooks.byTexturePath().size(),
                rank(unknown, Set.of()).stream().map(Usage::id).toList(), notes,
                attachableCount, attachablesWithoutGeometry);
    }

    private static void surveyBlock(BlockDefIr block, TerrainTextures terrain,
            LegacyBlockIndex legacy, Map<String, GeometryIr> geometries, AddonIr ir,
            List<String> untextured, List<String> pathA, Map<String, String> rejected) {
        // The same order the runtime resolves in: the modern component, then the legacy file.
        BlockModels.Materials materials = BlockModels.materialsOf(block.components());
        if (materials.isEmpty()) {
            materials = legacy.materialsFor(block.identifier()).orElse(materials);
        }
        // All the way to the FILE, not just to the path. A key that resolves to a path no pack
        // contains is the commonest way a block ends up with the missing texture, and a survey that
        // stopped at the path would report it as fine - which is the false negative this tool
        // exists to prevent, in the tool itself.
        boolean found = materials.textureFor("*")
                .flatMap(terrain::resolve)
                .filter(path -> exists(ir, path))
                .isPresent();
        if (!found) {
            untextured.add(block.identifier().toString());
        }

        Optional<String> wanted = BlockModels.geometryOf(block.components());
        if (wanted.isEmpty()) {
            return;
        }
        GeometryIr geometry = geometries.get(wanted.get());
        if (geometry == null) {
            rejected.put(block.identifier().toString(), "no pack declares " + wanted.get());
        } else if (BlockGeometry.transpilable(geometry)) {
            pathA.add(block.identifier().toString());
        } else {
            rejected.put(block.identifier().toString(),
                    wanted.get() + " needs path B (free rotation, poly mesh or binding)");
        }
    }

    /** Most used first, so the top of the list is the next thing worth implementing. */
    private static List<Usage> rank(Map<String, Integer> counts, Set<BedrockId> read) {
        List<Usage> usages = new ArrayList<>();
        counts.forEach((id, count) -> usages.add(
                new Usage(id, count, read.stream().anyMatch(r -> r.toString().equals(id)))));
        usages.sort(Comparator.comparingInt(Usage::count).reversed()
                .thenComparing(Usage::id));
        return usages;
    }

    private static Set<BedrockId> union(Set<BedrockId> first, Set<BedrockId> second) {
        Set<BedrockId> all = new java.util.LinkedHashSet<>(first);
        all.addAll(second);
        return all;
    }

    private static TerrainTextures merge(AddonIr ir, String path) {
        Map<String, List<String>> merged = new LinkedHashMap<>();
        for (PackIr pack : ir.packs()) {
            read(pack, path).ifPresent(text ->
                    merged.putAll(TerrainTextures.of(Json.parse(text)).byKey()));
        }
        return new TerrainTextures(merged);
    }

    private static LegacyBlockIndex legacy(AddonIr ir) {
        Map<String, Map<String, String>> textures = new LinkedHashMap<>();
        Map<String, String> sounds = new LinkedHashMap<>();
        for (PackIr pack : ir.packs()) {
            read(pack, "blocks.json").ifPresent(text -> {
                LegacyBlockIndex index = LegacyBlockIndex.of(Json.parse(text));
                textures.putAll(index.texturesByBlock());
                sounds.putAll(index.soundByBlock());
            });
        }
        return new LegacyBlockIndex(textures, sounds);
    }

    private static FlipbookTextures flipbooks(AddonIr ir) {
        Map<String, FlipbookTextures.Flipbook> merged = new LinkedHashMap<>();
        for (PackIr pack : ir.packs()) {
            read(pack, "textures/flipbook_textures.json").ifPresent(text ->
                    merged.putAll(FlipbookTextures.of(Json.parse(text)).byTexturePath()));
        }
        return new FlipbookTextures(merged);
    }

    /**
     * Whether a texture path exists in any pack, under any of the extensions Bedrock accepts.
     *
     * <p>The same three candidates, in the same order, the runtime tries. A different order here
     * would make the survey right about a file the runtime cannot find, or the reverse.
     */
    private static boolean exists(AddonIr ir, String path) {
        for (String candidate : List.of(path + ".png", path + ".tga", path)) {
            for (PackIr pack : ir.packs()) {
                try {
                    if (pack.source().vfs().read(candidate).isPresent()) {
                        return true;
                    }
                } catch (RuntimeException unreadable) {
                    // An unreadable archive is not this file's absence; keep looking.
                }
            }
        }
        return false;
    }

    /** Reads a pack file, answering empty for anything unreadable — a survey must not throw. */
    private static Optional<String> read(PackIr pack, String path) {
        try {
            return pack.source().vfs().read(path).map(source -> {
                try {
                    return source.readUtf8();
                } catch (IOException unreadable) {
                    return null;
                }
            });
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /** Renders a report as the lines a person reads. */
    public static List<String> render(Report report) {
        List<String> lines = new ArrayList<>();
        lines.add("packs " + report.packs() + ", blocks " + report.blocks()
                + ", items " + report.items());
        report.notes().forEach(note -> lines.add("  note: " + note));

        lines.add("");
        lines.add("block components, most used first (x = this build reads it):");
        report.blockComponents().forEach(usage -> lines.add(
                "  " + (usage.read() ? "x" : " ") + "  " + usage.count() + "  " + usage.id()));

        if (!report.itemComponents().isEmpty()) {
            lines.add("");
            lines.add("item components, most used first (x = this build reads it):");
            report.itemComponents().forEach(usage -> lines.add(
                    "  " + (usage.read() ? "x" : " ") + "  " + usage.count() + "  " + usage.id()));
        }

        lines.add("");
        lines.add("attachables: " + report.attachables() + " parsed, "
                + report.attachablesWithoutGeometry().size() + " naming a geometry no pack ships");
        report.attachablesWithoutGeometry().forEach(one -> lines.add("  " + one));

        lines.add("");
        lines.add("geometry: " + report.geometryPathA().size() + " transpilable, "
                + report.geometryRejected().size() + " not");
        report.geometryRejected().forEach((block, why) -> lines.add("  " + block + ": " + why));

        lines.add("");
        lines.add("textures: " + report.blocksWithoutTexture().size()
                + " block(s) resolve to no file, " + report.animatedTextures() + " animated");
        report.blocksWithoutTexture().forEach(block -> lines.add("  " + block));

        if (!report.unknownKeys().isEmpty()) {
            lines.add("");
            lines.add("keys kept but not modelled: " + String.join(", ", report.unknownKeys()));
        }
        return lines;
    }

    /** The wearer's state, as a comma-separated list of the things a controller asks about. */
    private static AttachableContext.Wearer wearerDoing(String doing) {
        java.util.Set<String> flags = new java.util.LinkedHashSet<>();
        for (String flag : doing.split(",")) {
            flags.add(flag.trim().toLowerCase(java.util.Locale.ROOT));
        }
        return new AttachableContext.Wearer(
                flags.contains("sneaking"),
                flags.contains("in_water"),
                flags.contains("swimming"),
                flags.contains("gliding"),
                flags.contains("sleeping"),
                flags.contains("on_fire"));
    }

    /** {@code ./gradlew --project-dir core :testkit:survey -Paddons=<path>} */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("usage: AddonSurvey <folder of add-ons> [<folder> ...]");
            return;
        }
        // A second argument beginning `geometry.` asks where that model's bones land instead of for
        // the whole survey. For "is this part missing, or is it somewhere else" — a question that
        // looks the same on screen and is a list of numbers here.
        if (args.length >= 2 && args[1].startsWith("geometry.")) {
            poseReport(Path.of(args[0]), args[1], args.length > 2 ? args[2] : null)
                    .forEach(System.out::println);
            return;
        }
        // `attachable.<identifier> [first|third]` poses a real attachable the way a VIEW does -
        // every entry of scripts.animate, conditions evaluated. Distinct from the line above, which
        // applies one named animation: composition is where the answers differ.
        if (args.length >= 2 && args[1].startsWith("attachable.")) {
            attachableReport(Path.of(args[0]), args[1].substring("attachable.".length()),
                    args.length > 2 ? args[2] : "third",
                    args.length > 3 ? args[3] : "main",
                    args.length > 4 ? args[4] : "",
                    args.length > 5 ? args[5] : "").forEach(System.out::println);
            return;
        }
        // Every folder as ONE set, because that is what the runtime sees. Surveying the behaviour
        // folder alone reports every texture as unresolvable - the pictures are in the resource
        // folder - which is a false alarm of exactly the kind this tool exists to stop.
        Path[] roots = Stream.of(args).map(Path::of).toArray(Path[]::new);
        render(of(roots)).forEach(System.out::println);
    }
}
