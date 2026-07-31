package net.nennneko5787.sweetcookie.core.format.ir.geometry;

import java.util.Locale;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * The six faces of a cube, named as Bedrock names them. SC-180 §3.
 *
 * <p>Bedrock's axis convention, not Java's — the IR preserves it and the renderer converts once
 * (SC-110 §6.1). The declaration order here is the order a canonical golden lists them in, and is
 * chosen to match the box-UV layout in {@link BoxUv} so that the two can be read side by side.
 */
@SpecImpl("SC-180")
public enum CubeFace {
    NORTH,
    SOUTH,
    EAST,
    WEST,
    UP,
    DOWN;

    /** The lowercase name a pack writes. */
    public String declared() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<CubeFace> parse(String raw) {
        for (CubeFace face : values()) {
            if (face.declared().equals(raw.trim().toLowerCase(Locale.ROOT))) {
                return Optional.of(face);
            }
        }
        return Optional.empty();
    }
}
