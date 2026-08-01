package net.nennneko5787.sweetcookie.runtime.addon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostic;
import net.nennneko5787.sweetcookie.core.format.ir.AddonIr;
import net.nennneko5787.sweetcookie.core.format.ir.IrLoader;
import net.nennneko5787.sweetcookie.core.format.ir.PackIr;
import net.nennneko5787.sweetcookie.core.format.pack.AddonLoader;
import net.nennneko5787.sweetcookie.core.format.pack.LoadedAddon;
import net.nennneko5787.sweetcookie.core.format.value.PackId;

/**
 * The installed add-ons, parsed. SC-120 §8, SC-280 §5.
 *
 * <p>The first thing in this project that runs the whole ingestion pipeline against real files:
 * {@code AddonLoader} finds and validates the packs, {@code IrLoader} parses their content, and what
 * comes out is a list of packs with what each provides and everything that went wrong reading it.
 *
 * <p><b>Installed, not activated.</b> SC-120 §8 installs packs per instance and activates them per
 * world; this is the instance half. Nothing here decides what a world uses.
 *
 * <p>Diagnostics are grouped by the pack they were reported against, because that is how a user
 * reads them — "this pack is broken" rather than "something in a load of nine packs is broken". A
 * diagnostic raised before any pack had an identity (a corrupt archive, an unusable manifest) has no
 * pack to group under and is kept separately rather than dropped.
 */
@SpecImpl({"SC-120", "SC-280"})
public final class AddonRegistry {

    private static final AddonRegistry EMPTY =
            new AddonRegistry(List.of(), List.of(), Optional.empty(), Optional.empty());

    private final List<PackSummary> packs;
    private final List<Diagnostic> unattributed;
    private final Optional<AddonIr> ir;
    private final Optional<Path> directory;

    private AddonRegistry(List<PackSummary> packs, List<Diagnostic> unattributed,
            Optional<AddonIr> ir, Optional<Path> directory) {
        this.packs = List.copyOf(packs);
        this.unattributed = List.copyOf(unattributed);
        this.ir = ir;
        this.directory = directory;
    }

    public static AddonRegistry empty() {
        return EMPTY;
    }

    /**
     * Scans {@code directory} and parses everything in it.
     *
     * <p>A missing directory is created and yields nothing, which is a fresh install rather than a
     * problem. Every entry is offered to the loader — archives and unpacked directories alike — and
     * one that is not an add-on simply produces no packs.
     */
    public static AddonRegistry scan(Path directory) {
        List<Path> candidates = candidatesIn(directory);
        if (candidates.isEmpty()) {
            // Scanned and empty, which is a different thing from not scanned yet. A screen that
            // said "no add-ons installed" before any scan would be reporting a fact it did not have.
            return new AddonRegistry(List.of(), List.of(), Optional.empty(), Optional.of(directory));
        }

        LoadedAddon loaded = AddonLoader.load(candidates);
        AddonIr parsed = IrLoader.parse(loaded);

        Map<PackId, List<Diagnostic>> byPack = new LinkedHashMap<>();
        List<Diagnostic> unattributed = new ArrayList<>();
        for (Diagnostic diagnostic : parsed.diagnostics().diagnostics()) {
            PackId pack = diagnostic.where().map(where -> where.pack()).orElse(PackId.NONE);
            if (pack.isNone()) {
                unattributed.add(diagnostic);
            } else {
                byPack.computeIfAbsent(pack, ignored -> new ArrayList<>()).add(diagnostic);
            }
        }

        List<PackSummary> summaries = new ArrayList<>();
        for (PackIr pack : parsed.packs()) {
            summaries.add(PackSummary.of(pack, byPack.getOrDefault(pack.id(), List.of())));
        }
        return new AddonRegistry(summaries, unattributed, Optional.of(parsed), Optional.of(directory));
    }

    private static List<Path> candidatesIn(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException uncreatable) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            // Sorted, so that two machines with the same folder produce the same load order before
            // SC-100 §5's tie-break ever runs. Filesystem enumeration order is not an input.
            return entries.sorted().toList();
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    /** Installed packs, in resolved load order. */
    public List<PackSummary> packs() {
        return packs;
    }

    /** Diagnostics raised before any pack had an identity. */
    public List<Diagnostic> unattributed() {
        return unattributed;
    }

    /** Where these came from, or empty when nothing has been scanned yet. */
    public Optional<Path> directory() {
        return directory;
    }

    /** The parsed content, when anything was loaded. */
    public Optional<AddonIr> ir() {
        return ir;
    }

    public boolean isEmpty() {
        return packs.isEmpty();
    }

}
