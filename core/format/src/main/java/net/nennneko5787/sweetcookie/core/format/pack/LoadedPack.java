package net.nennneko5787.sweetcookie.core.format.pack;

import java.util.List;
import java.util.Set;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.manifest.Capability;
import net.nennneko5787.sweetcookie.core.format.manifest.Manifest;
import net.nennneko5787.sweetcookie.core.format.manifest.PackDependency;
import net.nennneko5787.sweetcookie.core.format.manifest.PackHeader;
import net.nennneko5787.sweetcookie.core.format.manifest.PackModule;
import net.nennneko5787.sweetcookie.core.format.text.Localisation;
import net.nennneko5787.sweetcookie.core.format.value.PackId;
import net.nennneko5787.sweetcookie.core.format.value.SemanticVersion;
import net.nennneko5787.sweetcookie.core.format.value.Provenance;

/**
 * A pack whose manifest and texts have been read and nothing else. SC-100 §11.
 *
 * <p>No file inside the pack other than {@code manifest.json} and {@code texts/**} has been touched
 * at this point. Everything else is deferred to SC-110's parser dispatch, which reads through
 * {@link #vfs()}.
 *
 * <p>{@code vfs} is already layered with the selected subpack, so a consumer never has to know that
 * subpacks exist.
 *
 * @param manifest  the parsed manifest
 * @param subpacks  which variant is active
 * @param texts     {@code texts/}, every locale the pack ships
 * @param vfs       the pack's files, subpack overlay applied
 * @param source    where it came from, for diagnostics and reload
 * @param loadOrder position in the resolved order; higher wins (SC-100 §5, SC-110 §9.1)
 */
@SpecImpl("SC-100")
public record LoadedPack(
        Manifest manifest,
        SubpackSelection subpacks,
        Localisation texts,
        PackVfs vfs,
        PackSource source,
        int loadOrder) {

    /**
     * The pack's identity.
     *
     * <p>SC-100 §11 lists {@code id}, {@code version}, {@code header}, {@code modules},
     * {@code dependencies} and {@code capabilities} as separate fields. They are accessors here
     * instead: every one is already a field of {@link Manifest}, and duplicating them into the
     * record would create two places for the same fact to be edited.
     */
    public PackId id() {
        return manifest.header().id();
    }

    public SemanticVersion version() {
        return manifest.header().version();
    }

    public PackHeader header() {
        return manifest.header();
    }

    public List<PackModule> modules() {
        return manifest.modules();
    }

    public List<PackDependency> dependencies() {
        return manifest.dependencies();
    }

    public Set<Capability> capabilities() {
        return manifest.capabilities();
    }

    /** The pack name resolved against its own {@code texts/}, since it is often a {@code .lang} key. */
    public String displayName(String locale) {
        String raw = header().name();
        return raw.isEmpty() ? source.toString() : texts.resolve(raw, locale);
    }

    /** Provenance for a file inside this pack. */
    public Provenance provenanceOf(String path) {
        return Provenance.file(id(), path);
    }
}
