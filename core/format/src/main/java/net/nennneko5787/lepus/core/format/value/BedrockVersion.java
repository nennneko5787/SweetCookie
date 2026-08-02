package net.nennneko5787.lepus.core.format.value;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * A Bedrock engine or {@code format_version} number, such as {@code 1.26.30}. SC-110 §6.
 *
 * <p>Distinct from {@link SemanticVersion} on purpose. This is not SemVer: there is no prerelease
 * and no build metadata, a fourth component appears in engine builds ({@code 1.26.30.5}), and
 * missing trailing components mean zero rather than being an error. Modelling both with one type
 * meant, in practice, that a {@code format_version} of {@code 1.8} either failed to parse or
 * acquired prerelease semantics it does not have.
 *
 * <p>Note the creator-facing numbering: packs and this type say {@code 1.26.x} even though the game
 * is marketed as {@code 26.x} (SC-000 §4). Lepus's Minecraft-version nodes use the marketed
 * form; these two never mix.
 *
 * @param major    first component
 * @param minor    second component, 0 when absent
 * @param patch    third component, 0 when absent
 * @param revision fourth component, 0 when absent — engine builds carry one, packs never do
 */
@SpecImpl("SC-110")
public record BedrockVersion(int major, int minor, int patch, int revision)
        implements Comparable<BedrockVersion> {

    /** What a file with no {@code format_version} at all is treated as, before sniffing. */
    public static final BedrockVersion ZERO = new BedrockVersion(0, 0, 0, 0);

    public BedrockVersion {
        if (major < 0 || minor < 0 || patch < 0 || revision < 0) {
            throw new IllegalArgumentException("version components must not be negative");
        }
    }

    public static BedrockVersion of(int major, int minor, int patch) {
        return new BedrockVersion(major, minor, patch, 0);
    }

    /**
     * Parses {@code 1}, {@code 1.8}, {@code 1.8.0} or {@code 1.26.30.5}.
     *
     * @throws IllegalArgumentException if the text is not a dotted run of non-negative integers
     */
    public static BedrockVersion parse(String text) {
        return tryParse(text).orElseThrow(
                () -> new IllegalArgumentException("not a Bedrock version: " + text));
    }

    /** Parses, or returns empty. The caller decides which diagnostic a failure deserves. */
    public static Optional<BedrockVersion> tryParse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String[] parts = text.trim().split("\\.", -1);
        if (parts.length == 0 || parts.length > 4) {
            return Optional.empty();
        }
        int[] out = new int[4];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                return Optional.empty();
            }
            for (int c = 0; c < parts[i].length(); c++) {
                if (parts[i].charAt(c) < '0' || parts[i].charAt(c) > '9') {
                    return Optional.empty();
                }
            }
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException overflow) {
                return Optional.empty();
            }
        }
        return Optional.of(new BedrockVersion(out[0], out[1], out[2], out[3]));
    }

    /**
     * Normalises the array form, which {@code min_engine_version} uses.
     *
     * <p>Lenient like {@link SemanticVersion#fromArray}: missing elements are 0, extras are dropped.
     */
    public static BedrockVersion fromArray(List<Integer> parts) {
        Objects.requireNonNull(parts, "parts");
        return new BedrockVersion(at(parts, 0), at(parts, 1), at(parts, 2), at(parts, 3));
    }

    private static int at(List<Integer> parts, int index) {
        if (index >= parts.size()) {
            return 0;
        }
        Integer value = parts.get(index);
        return value == null ? 0 : Math.max(0, value);
    }

    /** Trailing zero components are omitted, but never below three: {@code 1.8.0}, not {@code 1.8}. */
    @Override
    public String toString() {
        String base = major + "." + minor + "." + patch;
        return revision == 0 ? base : base + "." + revision;
    }

    @Override
    public int compareTo(BedrockVersion other) {
        int cmp = Integer.compare(major, other.major);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(minor, other.minor);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(patch, other.patch);
        return cmp != 0 ? cmp : Integer.compare(revision, other.revision);
    }

    public boolean isAtLeast(BedrockVersion other) {
        return compareTo(other) >= 0;
    }
}
