package net.nennneko5787.lepus.core.format.ir.geometry;

import java.util.List;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.UnknownData;
import net.nennneko5787.lepus.core.format.value.Provenance;
import net.nennneko5787.lepus.core.format.value.Vec3f;

/**
 * One model, normalised out of either file family. SC-180 §3.
 *
 * @param identifier    {@code geometry.<name>}, as the pack spells it
 * @param parent        the inherited model, from the {@code geometry.a:geometry.b} syntax
 * @param sourceFamily  which family the file was in; for diagnostics and round trips only
 * @param textureWidth  the divisor for {@code uv}; {@code texturewidth} or {@code texture_width}
 * @param textureHeight likewise
 * @param visibleBounds the culling box, when the model declares one
 * @param bones         a flat list in declaration order; the hierarchy is by {@code parent} name
 * @param provenance    pack, file, position, declared and effective version (SC-110 §4)
 * @param unknown       keys this build does not recognise, kept verbatim (SC-110 §5)
 */
@SpecImpl("SC-180")
public record GeometryIr(
        String identifier,
        Optional<String> parent,
        GeometryFamily sourceFamily,
        int textureWidth,
        int textureHeight,
        Optional<VisibleBounds> visibleBounds,
        List<BoneIr> bones,
        Provenance provenance,
        UnknownData unknown) {

    /**
     * What a model that omits its texture size is treated as.
     *
     * <p>Bedrock's own default. A model with no {@code texturewidth} is common in hand-written
     * {@code 1.8.0} files, and treating the divisor as zero would put every UV at infinity.
     */
    public static final int DEFAULT_TEXTURE_SIZE = 16;

    public GeometryIr {
        bones = List.copyOf(bones);
    }

    /** The culling box, SC-180 §3. */
    public record VisibleBounds(float width, float height, Vec3f offset) {
    }

    /** The bone of that name, if the model declares one. */
    public Optional<BoneIr> bone(String name) {
        return bones.stream().filter(bone -> bone.name().equals(name)).findFirst();
    }

    /** Total cubes across every bone — the cheap size metric a diagnostic can quote. */
    public int cubeCount() {
        return bones.stream().mapToInt(bone -> bone.cubes().size()).sum();
    }
}
