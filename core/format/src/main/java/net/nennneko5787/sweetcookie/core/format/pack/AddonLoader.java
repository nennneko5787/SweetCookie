package net.nennneko5787.sweetcookie.core.format.pack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostics;
import net.nennneko5787.sweetcookie.core.format.diag.FormatDiagnostics;
import net.nennneko5787.sweetcookie.core.format.json.Json;
import net.nennneko5787.sweetcookie.core.format.json.JsonObject;
import net.nennneko5787.sweetcookie.core.format.manifest.Manifest;
import net.nennneko5787.sweetcookie.core.format.manifest.ManifestParser;
import net.nennneko5787.sweetcookie.core.format.manifest.ModuleType;
import net.nennneko5787.sweetcookie.core.format.manifest.PackDependency;
import net.nennneko5787.sweetcookie.core.format.text.Localisation;
import net.nennneko5787.sweetcookie.core.format.value.PackId;
import net.nennneko5787.sweetcookie.core.format.value.Provenance;

/**
 * Turns files on disk into packs in a resolved, deterministic order. SC-100.
 *
 * <p>This is the whole of SC-100's output side: discovery, manifests, deduplication, load order,
 * engine gating, subpack selection, localisation and dependency resolution. Nothing inside a pack
 * other than {@code manifest.json} and {@code texts/**} is read — SC-110's dispatcher does the rest,
 * lazily, through {@link LoadedPack#vfs()}.
 *
 * <p>Failure never propagates past the pack it belongs to. An unreadable archive, a manifest with no
 * UUID, a compression bomb: each removes one pack and leaves the others alone.
 */
@SpecImpl("SC-100")
public final class AddonLoader {

    private AddonLoader() {
    }

    public static LoadedAddon load(List<Path> sources) {
        return load(sources, LoadOptions.DEFAULT);
    }

    /** Loads every pack in {@code sources}. Close the result when done (SC-100 §12). */
    public static LoadedAddon load(List<Path> sources, LoadOptions options) {
        Diagnostics into = new Diagnostics();
        List<PackVfs> containers = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();

        for (Path source : sources) {
            PackDiscovery.Result discovered =
                    PackDiscovery.scan(source, options.limits(), into);
            containers.addAll(discovered.containers());
            for (PackDiscovery.Found found : discovered.packs()) {
                readManifest(found, into).ifPresent(
                        manifest -> candidates.add(new Candidate(manifest, found)));
            }
        }

        List<Candidate> ordered = sortIntoLoadOrder(candidates, options);
        List<Candidate> deduplicated = deduplicate(ordered, into);

        List<LoadedPack> packs = new ArrayList<>();
        for (int i = 0; i < deduplicated.size(); i++) {
            packs.add(finish(deduplicated.get(i), i, options, into));
        }
        resolveDependencies(packs, options, into);

        return new LoadedAddon(packs, into.snapshot(), containers);
    }

    private record Candidate(Manifest manifest, PackDiscovery.Found found) {
    }

    private static Optional<Manifest> readManifest(PackDiscovery.Found found, Diagnostics into) {
        Provenance where = Provenance.file(PackId.NONE, found.source() + "/manifest.json");
        Optional<ByteSource> bytes = found.vfs().read("manifest.json");
        if (bytes.isEmpty()) {
            return Optional.empty(); // discovery found it; a race or a broken archive lost it
        }
        String text;
        try {
            text = bytes.get().readUtf8();
        } catch (IOException e) {
            into.report(FormatDiagnostics.MANIFEST_UNUSABLE.at(where, e.toString()));
            return Optional.empty();
        }
        Optional<JsonObject> root = Json.tryParseObject(text, where, into);
        return root.flatMap(json -> ManifestParser.parse(json, where, into));
    }

    /**
     * SC-100 §5.
     *
     * <p>Packs the world's activation file names come <b>last</b>, in that file's order, so the
     * final entry wins; packs it does not name come first, sorted by
     * {@code (sanitised source path, header.uuid)}. Both halves are total orders over strings and
     * neither depends on filesystem enumeration or on archive entry order — re-zipping an add-on
     * must not reshuffle which pack overrides which.
     *
     * <p>SC-100 §5 originally said only "in decreasing precedence", which does not say whether an
     * explicitly ordered pack outranks an unlisted one. It was resolved here in favour of the
     * explicit choice and written back into the specification.
     */
    private static List<Candidate> sortIntoLoadOrder(
            List<Candidate> candidates, LoadOptions options) {
        Map<PackId, Integer> explicit = new LinkedHashMap<>();
        for (int i = 0; i < options.activationOrder().size(); i++) {
            explicit.putIfAbsent(options.activationOrder().get(i), i);
        }
        Comparator<Candidate> order = Comparator
                .<Candidate, Integer>comparing(c ->
                        explicit.containsKey(c.manifest().header().id()) ? 1 : 0)
                .thenComparing(c -> explicit.getOrDefault(c.manifest().header().id(), 0))
                .thenComparing(c -> c.found().source().sortKey())
                .thenComparing(c -> c.manifest().header().id());
        return candidates.stream().sorted(order).toList();
    }

    /**
     * SC-100 §4.4.
     *
     * <p>Same UUID and same version: the later in load order wins entirely ({@code SCE-1026}). Same
     * UUID, different versions: the highest version wins ({@code SCE-1027}), because that is what
     * Bedrock does and because a pack listing an old copy of itself is a packaging accident rather
     * than an override.
     */
    private static List<Candidate> deduplicate(List<Candidate> ordered, Diagnostics into) {
        Map<PackId, Candidate> winners = new LinkedHashMap<>();
        for (Candidate candidate : ordered) {
            PackId id = candidate.manifest().header().id();
            Candidate previous = winners.get(id);
            if (previous == null) {
                winners.put(id, candidate);
                continue;
            }
            Provenance where =
                    Provenance.file(PackId.NONE, candidate.found().source().toString());
            int byVersion = candidate.manifest().version().compareTo(previous.manifest().version());
            if (byVersion == 0) {
                into.report(FormatDiagnostics.PACK_DUPLICATE.at(
                        where, id.toString(), previous.found().source().toString()));
                winners.put(id, candidate); // later in load order
            } else {
                into.report(FormatDiagnostics.PACK_DUPLICATE_VERSIONS.at(
                        where,
                        id.toString(),
                        previous.manifest().version().toString(),
                        candidate.manifest().version().toString()));
                if (byVersion > 0) {
                    winners.put(id, candidate);
                }
            }
        }
        return List.copyOf(winners.values());
    }

    private static LoadedPack finish(
            Candidate candidate, int loadOrder, LoadOptions options, Diagnostics into) {
        Manifest manifest = candidate.manifest();
        Provenance where = Provenance.file(manifest.header().id(),
                candidate.found().source() + "/manifest.json");

        // SC-100 §6. A pack that needs a newer engine loads anyway: refusing would make the mod
        // useless the day Bedrock ships an update, and most such packs work regardless.
        if (manifest.header().minEngineVersion().compareTo(options.targetEngine()) > 0) {
            into.report(FormatDiagnostics.ENGINE_VERSION_AHEAD.at(
                    where,
                    manifest.header().minEngineVersion().toString(),
                    options.targetEngine().toString()));
        }

        // SC-100 §4.2. One report per capability per pack: each is independently unsupported, and
        // collapsing them would hide which one a player is missing.
        manifest.capabilities().forEach(capability ->
                into.report(FormatDiagnostics.CAPABILITY_UNSUPPORTED.at(where, capability.declared()),
                        List.of(where, capability)));
        manifest.unknownCapabilities().forEach(capability ->
                into.report(FormatDiagnostics.CAPABILITY_UNSUPPORTED.at(where, capability),
                        List.of(where, capability)));

        SubpackSelection subpacks = SubpackSelection.choose(
                manifest.header().subpacks(), options.memoryTierCeiling());
        PackVfs vfs = subpacks.applyTo(candidate.found().vfs());

        return new LoadedPack(
                manifest, subpacks, Localisation.read(vfs), vfs, candidate.found().source(),
                loadOrder);
    }

    /**
     * SC-100 §10.
     *
     * <p>Everything here warns and nothing refuses. A dependency on a pack that is not loaded is
     * routine — real packs list stale dependencies constantly and Bedrock itself only warns — and
     * <b>cycles are permitted</b>, because a behavior pack and its paired resource pack depending on
     * each other is the common case. The graph is never topologically sorted; where ordering
     * matters, §5 governs.
     */
    private static void resolveDependencies(
            List<LoadedPack> packs, LoadOptions options, Diagnostics into) {
        Map<PackId, LoadedPack> byId = new LinkedHashMap<>();
        packs.forEach(pack -> byId.put(pack.id(), pack));

        for (LoadedPack pack : packs) {
            Provenance where = pack.provenanceOf("manifest.json");
            for (PackDependency dependency : pack.dependencies()) {
                switch (dependency) {
                    case PackDependency.OnPack onPack -> {
                        LoadedPack target = byId.get(onPack.uuid());
                        if (target == null) {
                            into.report(FormatDiagnostics.DEPENDENCY_MISSING.at(
                                    where, onPack.uuid().toString()),
                                    List.of(where, onPack.uuid()));
                        } else if (target.version().compareTo(onPack.version()) < 0) {
                            into.report(FormatDiagnostics.DEPENDENCY_VERSION_AHEAD.at(
                                    where,
                                    onPack.uuid().toString(),
                                    onPack.version().toString(),
                                    target.version().toString()),
                                    List.of(where, onPack.uuid()));
                        }
                    }
                    case PackDependency.OnModule onModule -> {
                        if (!options.supportedScriptModules().contains(onModule.moduleName())) {
                            into.report(FormatDiagnostics.SCRIPT_MODULE_UNSUPPORTED.at(
                                    where, onModule.moduleName(),
                                    onModule.version().toString()),
                                    List.of(where, onModule.moduleName()));
                        }
                    }
                }
            }
        }
    }
}
