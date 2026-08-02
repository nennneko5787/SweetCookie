package net.nennneko5787.lepus.core.format.ir.geometry;

import java.util.List;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.UnknownData;
import net.nennneko5787.lepus.core.format.value.Vec3f;

/**
 * One bone. SC-180 §3.
 *
 * <p>The hierarchy is expressed by {@code parent} naming another bone, <b>not</b> by nesting, and the
 * IR keeps it that way. Bedrock's own files are a flat list in arbitrary order, a child may appear
 * before its parent, and a parent may not exist at all — resolving that into a tree at parse time
 * would mean either rejecting files Bedrock loads or inventing a root. Resolution is a later pass
 * with its own diagnostic.
 *
 * @param name      the bone's name, as the pack spells it
 * @param parent    the parent bone's name, when it declares one
 * @param pivot     rotation origin, in Bedrock's convention
 * @param rotation  degrees about {@code pivot}
 * @param bind      {@code binding}, verbatim — see the note below
 * @param mirror    mirrors every cube's UV
 * @param inflate   grows every cube by this
 * @param neverRender {@code never_render}: the bone positions children and draws nothing itself
 * @param cubes     boxes, in declaration order
 * @param locators  attachment points, sorted by name so a golden is stable
 * @param unknown   keys this build does not recognise, kept verbatim (SC-110 §5)
 */
@SpecImpl("SC-180#geometry/bones")
public record BoneIr(
        String name,
        Optional<String> parent,
        Vec3f pivot,
        Vec3f rotation,
        Optional<String> bind,
        boolean mirror,
        float inflate,
        boolean neverRender,
        List<CubeIr> cubes,
        List<LocatorIr> locators,
        UnknownData unknown) {

    public BoneIr {
        cubes = List.copyOf(cubes);
        locators = List.copyOf(locators);
    }

    /**
     * {@code binding} as written, not parsed.
     *
     * <p>SC-110 §7 requires Molang to be parsed at ingest and never stored as text, and this field is
     * Molang. It is therefore <b>not</b> exposed as a {@code MolangExpr} and nothing may evaluate it:
     * the expression layer does not exist yet, and a raw string that looks evaluable is exactly how a
     * parse error ends up surfacing mid-frame with no provenance. It is kept only so that the round
     * trip is lossless and so a diagnostic can name it. {@code geometry/binding} stays {@code stub}
     * until SC-130 lands.
     */
    @Override
    public Optional<String> bind() {
        return bind;
    }
}
