package net.nennneko5787.sweetcookie.core.format.value;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * The identity of a pack: {@code manifest.json}'s {@code header.uuid}. SC-100 §4.4.
 *
 * <p>Parsed leniently. Real packs ship malformed UUIDs often enough that rejecting them would reject
 * useful content, so a malformed value becomes {@link #derived}, which is stable for a given input
 * string. The pack still loads and still has an identity that survives a reload — which is what the
 * ledger needs (SC-120), and the reason a random UUID would be wrong here.
 *
 * @param uuid the parsed or derived identity
 */
@SpecImpl("SC-100")
public record PackId(UUID uuid) implements Comparable<PackId> {

    /**
     * The identity used for content that came from no pack.
     *
     * <p>Exists so that {@link Provenance} can name a pack unconditionally rather than holding a
     * nullable one — SC-110 §2 forbids {@code null} in the IR, and an {@code Optional<PackId>} on
     * every node would cost more than this sentinel.
     */
    public static final PackId NONE = new PackId(new UUID(0L, 0L));

    public PackId {
        Objects.requireNonNull(uuid, "uuid");
    }

    public static PackId of(UUID uuid) {
        return new PackId(uuid);
    }

    /** Parses a well-formed UUID, or returns empty. */
    public static Optional<PackId> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PackId(UUID.fromString(raw.trim())));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /**
     * The fallback for a malformed {@code header.uuid}: a name-based UUID over the raw string.
     *
     * <p>Deterministic, so the same broken manifest yields the same identity on every load and on
     * every machine. The caller emits {@code SCE-1025}.
     */
    public static PackId derived(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new PackId(UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)));
    }

    public boolean isNone() {
        return NONE.equals(this);
    }

    @Override
    public String toString() {
        return uuid.toString();
    }

    @Override
    public int compareTo(PackId other) {
        return uuid.compareTo(other.uuid);
    }
}
