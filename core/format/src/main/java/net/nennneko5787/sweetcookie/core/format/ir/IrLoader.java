package net.nennneko5787.sweetcookie.core.format.ir;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostics;
import net.nennneko5787.sweetcookie.core.format.diag.FormatDiagnostics;
import net.nennneko5787.sweetcookie.core.format.ir.block.BlockDefIr;
import net.nennneko5787.sweetcookie.core.format.ir.block.BlockFiles;
import net.nennneko5787.sweetcookie.core.format.ir.geometry.GeometryFiles;
import net.nennneko5787.sweetcookie.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.sweetcookie.core.format.value.BedrockId;
import net.nennneko5787.sweetcookie.core.format.json.Json;
import net.nennneko5787.sweetcookie.core.format.json.JsonObject;
import net.nennneko5787.sweetcookie.core.format.pack.ByteSource;
import net.nennneko5787.sweetcookie.core.format.pack.LoadedAddon;
import net.nennneko5787.sweetcookie.core.format.pack.LoadedPack;
import net.nennneko5787.sweetcookie.core.format.pack.VfsPath;
import net.nennneko5787.sweetcookie.core.format.value.Provenance;

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
        return new BehaviorIr(blocks);
    }

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
        return new ResourceIr(geometries);
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
