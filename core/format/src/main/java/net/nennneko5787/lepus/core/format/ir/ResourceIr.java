package net.nennneko5787.lepus.core.format.ir;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;

/**
 * The resource-pack half of one pack's IR. SC-110 §8.2.
 *
 * <p><b>One field so far.</b> SC-110 §8.2 lists client entities, render controllers, animations,
 * particles, attachables, materials, atlases and sounds alongside geometry; each arrives with its
 * own domain document and its own conformance cases. An empty map is the honest representation of
 * "not parsed yet" — it is not a claim that the pack has none, and the coverage ledger is where that
 * distinction is recorded.
 *
 * <p>Raw binary assets are deliberately absent (SC-110 §8.2): the IR holds paths and the asset
 * pipeline reads them through the VFS on demand. Loading a 300 MB texture set into memory to find
 * out what is in it would be indefensible.
 *
 * @param geometries keyed by {@code geometry.<name>}, in the order the pack's files were walked
 */
@SpecImpl("SC-110")
public record ResourceIr(Map<String, GeometryIr> geometries) {

    public static final ResourceIr EMPTY = new ResourceIr(Map.of());

    public ResourceIr {
        geometries = Collections.unmodifiableMap(new LinkedHashMap<>(geometries));
    }

    public Optional<GeometryIr> geometry(String identifier) {
        return Optional.ofNullable(geometries.get(identifier));
    }

    public boolean isEmpty() {
        return geometries.isEmpty();
    }
}
