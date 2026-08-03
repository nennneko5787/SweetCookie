package net.nennneko5787.lepus.core.format.ir;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.diag.FormatDiagnostics;
import net.nennneko5787.lepus.core.format.ir.block.BlockDefIr;
import net.nennneko5787.lepus.core.format.ir.block.BlockFiles;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryFiles;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.pack.ByteSource;
import net.nennneko5787.lepus.core.format.pack.LoadedAddon;
import net.nennneko5787.lepus.core.format.pack.LoadedPack;
import net.nennneko5787.lepus.core.format.pack.VfsPath;
import net.nennneko5787.lepus.core.format.value.Provenance;

/**
 * Walks a loaded add-on and parses its files into the IR. SC-110 §8.
 *
 * <p>Everything here is per pack and independent: nothing is merged across packs, because merge
 * rules differ per content kind (SC-110 §9.1) and doing it here would erase the difference between
 * "a later resource pack replaced this texture", which is normal, and "two behavior packs define
 * this entity", which is an authoring bug.
 *
 * <p>A file that cannot be read costs that file and nothing else. Constitution rule 1, and it is why
 * every step below reports and continues rather than throwing.
 */
@SpecImpl("SC-110")
public final class IrLoader {

    /**
     * Where Bedrock looks for models.
     *
     * <p>The whole directory, recursively, and by extension rather than by the {@code .geo.json}
     * convention: the convention is near-universal and is not enforced by Bedrock, and a model named
     * {@code wizard.json} loads there today.
     */
    private static final String MODELS_ROOT = "models";

    private IrLoader() {
    }

    /** Parses every pack in {@code addon}, in load order. */
    public static AddonIr parse(LoadedAddon addon) {
        Diagnostics into = new Diagnostics();
        List<PackIr> packs = new ArrayList<>();
        for (LoadedPack pack : addon.packs()) {
            packs.add(new PackIr(pack, behavior(pack, into), resources(pack, into)));
        }
        // SC-100's diagnostics first, then SC-110's: the order a user reads them in matches the
        // order the failures happened in, and a manifest problem explains a parse problem far more
        // often than the reverse.
        return new AddonIr(packs, addon.diagnostics().merge(into.snapshot()));
    }

    /** Where Bedrock looks for block definitions. */
    private static final String BLOCKS_ROOT = "blocks";

    private static BehaviorIr behavior(LoadedPack pack, Diagnostics into) {
        if (!pack.manifest().hasBehavior()) {
            return BehaviorIr.EMPTY;
        }
        Map<BedrockId, BlockDefIr> blocks = new LinkedHashMap<>();
        for (String path : pack.vfs().walk(BLOCKS_ROOT).sorted().toList()) {
            if (!VfsPath.extension(path).equals("json")) {
                continue;
            }
            Provenance where = pack.provenanceOf(path);
            Optional<JsonObject> root = read(pack, path, where, into);
            if (root.isEmpty()) {
                continue;
            }
            for (BlockDefIr block : BlockFiles.parse(root.get(), where, into)) {
                if (blocks.put(block.identifier(), block) != null) {
                    into.report(IrDiagnostics.FIELD_MALFORMED.at(
                            where, "identifier", "duplicate " + block.identifier()));
                }
            }
        }
        Map<BedrockId, net.nennneko5787.lepus.core.format.ir.item.ItemDefIr> items =
                new LinkedHashMap<>();
        for (String path : pack.vfs().walk(ITEMS_ROOT).sorted().toList()) {
            if (!VfsPath.extension(path).equals("json")) {
                continue;
            }
            Provenance where = pack.provenanceOf(path);
            Optional<JsonObject> root = read(pack, path, where, into);
            if (root.isEmpty()) {
                continue;
            }
            for (var item : net.nennneko5787.lepus.core.format.ir.item.ItemFiles
                    .parse(root.get(), where, into)) {
                if (items.put(item.identifier(), item) != null) {
                    into.report(IrDiagnostics.FIELD_MALFORMED.at(
                            where, "identifier", "duplicate " + item.identifier()));
                }
            }
        }
        return new BehaviorIr(blocks, items);
    }

    /** Where Bedrock looks for item definitions. */
    private static final String ITEMS_ROOT = "items";

    /** Where Bedrock looks for attachables — resource pack only. */
    private static final String ATTACHABLES_ROOT = "attachables";

    /** Where Bedrock looks for animations — resource pack only. */
    private static final String ANIMATIONS_ROOT = "animations";

    /** Where Bedrock looks for animation controllers — the resource pack's, which play animations. */
    private static final String ANIMATION_CONTROLLERS_ROOT = "animation_controllers";

    private static ResourceIr resources(LoadedPack pack, Diagnostics into) {
        if (!pack.manifest().hasResources()) {
            return ResourceIr.EMPTY;
        }
        Map<String, GeometryIr> geometries = new LinkedHashMap<>();
        for (String path : pack.vfs().walk(MODELS_ROOT).sorted().toList()) {
            if (!VfsPath.extension(path).equals("json")) {
                continue;
            }
            Provenance where = pack.provenanceOf(path);
            Optional<JsonObject> root = read(pack, path, where, into);
            if (root.isEmpty()) {
                continue;
            }
            for (GeometryIr geometry : GeometryFiles.parse(root.get(), where, into)) {
                GeometryIr previous = geometries.put(geometry.identifier(), geometry);
                if (previous != null) {
                    // Within ONE pack. Cross-pack overrides are normal and are SC-110 §9.1's
                    // business; the same identifier twice inside one pack is an authoring mistake
                    // whose second definition silently won.
                    into.report(IrDiagnostics.FIELD_MALFORMED.at(
                            where, "identifier", "duplicate " + geometry.identifier()));
                }
            }
        }
        // The resource pack's OWN items/, which is where minecraft:icon lives. A behaviour pack's
        // items/ of the same name carries what the item does and never what it looks like, so a
        // reader that saw only that one leaves every item in such a pack with no picture.
        Map<BedrockId, net.nennneko5787.lepus.core.format.ir.item.ItemDefIr> icons =
                new LinkedHashMap<>();
        for (String path : pack.vfs().walk(ITEMS_ROOT).sorted().toList()) {
            if (!VfsPath.extension(path).equals("json")) {
                continue;
            }
            Provenance where = pack.provenanceOf(path);
            read(pack, path, where, into).ifPresent(root ->
                    net.nennneko5787.lepus.core.format.ir.item.ItemFiles
                            .parse(root, where, into)
                            .forEach(item -> icons.put(item.identifier(), item)));
        }
        // Attachables: the 3D model an item is held or worn as (SC-170 §5). Resource-pack only,
        // and keyed by the ITEM identifier rather than by a name of its own — which is what makes
        // "what does this stack look like in a hand" answerable without an index.
        Map<BedrockId, net.nennneko5787.lepus.core.format.ir.attachable.AttachableIr>
                attachables = new LinkedHashMap<>();
        for (String path : pack.vfs().walk(ATTACHABLES_ROOT).sorted().toList()) {
            if (!VfsPath.extension(path).equals("json")) {
                continue;
            }
            Provenance where = pack.provenanceOf(path);
            read(pack, path, where, into).ifPresent(root ->
                    net.nennneko5787.lepus.core.format.ir.attachable.AttachableFiles
                            .parse(root, where, into)
                            .forEach(one -> attachables.put(one.identifier(), one)));
        }
        // Animations: what each bone does over time (SC-180 §4). Where a held model's POSITION comes
        // from as much as its motion — an attachable has no placement of its own, so the animation
        // that sets its root bone is the whole reason it sits where it does.
        Map<String, net.nennneko5787.lepus.core.format.ir.animation.AnimationIr> animations =
                new LinkedHashMap<>();
        for (String path : pack.vfs().walk(ANIMATIONS_ROOT).sorted().toList()) {
            if (!VfsPath.extension(path).equals("json")) {
                continue;
            }
            Provenance where = pack.provenanceOf(path);
            read(pack, path, where, into).ifPresent(root ->
                    net.nennneko5787.lepus.core.format.ir.animation.AnimationFiles
                            .parse(root, where, into)
                            .forEach(one -> animations.put(one.name(), one)));
        }
        // Animation controllers: the state machine that decides WHICH animation plays (SC-180 §5).
        // Every attachable in the surveyed corpus names one and none of them was ever read, so
        // sneaking, swimming, burning and sleeping were authored and never drawn.
        Map<String, net.nennneko5787.lepus.core.format.ir.animation.AnimationControllerIr>
                controllers = new LinkedHashMap<>();
        for (String path : pack.vfs().walk(ANIMATION_CONTROLLERS_ROOT).sorted().toList()) {
            if (!VfsPath.extension(path).equals("json")) {
                continue;
            }
            Provenance where = pack.provenanceOf(path);
            read(pack, path, where, into).ifPresent(root ->
                    net.nennneko5787.lepus.core.format.ir.animation.AnimationControllerFiles
                            .parse(root, where, into)
                            .forEach(one -> controllers.put(one.name(), one)));
        }
        return new ResourceIr(geometries, icons, attachables, animations, controllers);
    }

    private static Optional<JsonObject> read(
            LoadedPack pack, String path, Provenance where, Diagnostics into) {
        Optional<ByteSource> bytes = pack.vfs().read(path);
        if (bytes.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Json.tryParseObject(bytes.get().readUtf8(), where, into);
        } catch (IOException e) {
            into.report(FormatDiagnostics.JSON_MALFORMED.at(where, e.toString(), 0, 0));
            return Optional.empty();
        }
    }
}
