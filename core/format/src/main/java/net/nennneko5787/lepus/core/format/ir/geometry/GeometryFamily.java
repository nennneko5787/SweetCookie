package net.nennneko5787.lepus.core.format.ir.geometry;

import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.value.BedrockVersion;

/**
 * Which of the two structurally incompatible geometry file families a model came from. SC-180 §3.
 *
 * <p>Recorded on the IR node deliberately, and it is the one place SC-110 §3.2's "the IR never
 * encodes which version this came from" is relaxed — because this is not a version, it is a
 * statement about what the author wrote. A diagnostic that says "your file declares 1.8.0 and is in
 * the modern shape" needs it, and so does a round-trip test.
 *
 * <p>Nothing downstream may branch on it. Both families normalise to the same IR: per-face UV,
 * one bone list, one cube list.
 */
@SpecImpl("SC-180")
public enum GeometryFamily {

    /** Top-level {@code geometry.*} keys, {@code texturewidth}, box UV only. */
    LEGACY_1_8(BedrockVersion.of(1, 8, 0), "legacy_1_8"),

    /** {@code minecraft:geometry} array, {@code texture_width}, per-face UV. */
    MODERN(BedrockVersion.of(1, 12, 0), "modern");

    private final BedrockVersion version;
    private final String declared;

    GeometryFamily(BedrockVersion version, String declared) {
        this.version = version;
        this.declared = declared;
    }

    /** The version this family is registered under in the parser ladder. */
    public BedrockVersion version() {
        return version;
    }

    /** The stable spelling used in goldens and diagnostics. */
    public String declared() {
        return declared;
    }
}
