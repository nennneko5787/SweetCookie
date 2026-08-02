package net.nennneko5787.lepus.core.format.value;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * A pack or module version. SC-100 §4.3.
 *
 * <p>Bedrock writes this two ways and both normalise here: {@code manifest.json} format 1 and 2 use
 * {@code [major, minor, patch]}, and format 3 uses a SemVer string. Downstream code never learns
 * which shape it came from — that is the whole point of normalising at ingest.
 *
 * <p>Comparison is SemVer 2.0 precedence: build metadata is ignored, a prerelease sorts below the
 * release it precedes, and prerelease identifiers compare numerically when both are numeric.
 * Bedrock itself is looser than this, but no observed real-world pack depends on the difference.
 *
 * <p>{@code prerelease} and {@code build} use the empty string as the documented "absent" sentinel
 * rather than {@code null} (SC-110 §2); {@link #prerelease()} and {@link #build()} return the raw
 * component, {@link #prereleaseIfAny()} and {@link #buildIfAny()} the {@link Optional} view.
 */
@SpecImpl("SC-100")
public record SemanticVersion(int major, int minor, int patch, String prerelease, String build)
        implements Comparable<SemanticVersion> {

    /** What an absent version normalises to. Also what a malformed one degrades to. */
    public static final SemanticVersion ZERO = new SemanticVersion(0, 0, 0, "", "");

    public SemanticVersion {
        Objects.requireNonNull(prerelease, "prerelease");
        Objects.requireNonNull(build, "build");
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException(
                    "version components must not be negative: " + major + "." + minor + "." + patch);
        }
    }

    public static SemanticVersion of(int major, int minor, int patch) {
        return new SemanticVersion(major, minor, patch, "", "");
    }

    /**
     * Normalises {@code manifest.json}'s {@code [major, minor, patch]} array form.
     *
     * <p>Lenient by design, per SC-100 §4.3: missing elements default to 0 and extra elements are
     * dropped. The caller reports {@code SCE-1024} when {@code parts.size() != 3}; this method does
     * not, because it has no provenance to report it against.
     */
    public static SemanticVersion fromArray(List<Integer> parts) {
        Objects.requireNonNull(parts, "parts");
        return new SemanticVersion(at(parts, 0), at(parts, 1), at(parts, 2), "", "");
    }

    private static int at(List<Integer> parts, int index) {
        if (index >= parts.size()) {
            return 0;
        }
        Integer value = parts.get(index);
        return value == null ? 0 : Math.max(0, value);
    }

    /**
     * Parses the SemVer string form used by {@code manifest.json} format 3.
     *
     * @throws IllegalArgumentException if {@code text} is not a SemVer string
     */
    public static SemanticVersion parse(String text) {
        return tryParse(text).orElseThrow(
                () -> new IllegalArgumentException("not a semantic version: " + text));
    }

    /** Parses, or returns empty. The caller decides which diagnostic a failure deserves. */
    public static Optional<SemanticVersion> tryParse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String rest = text.trim();

        String build = "";
        int plus = rest.indexOf('+');
        if (plus >= 0) {
            build = rest.substring(plus + 1);
            rest = rest.substring(0, plus);
            if (!isDotSeparatedIdentifiers(build)) {
                return Optional.empty();
            }
        }

        String prerelease = "";
        int hyphen = rest.indexOf('-');
        if (hyphen >= 0) {
            prerelease = rest.substring(hyphen + 1);
            rest = rest.substring(0, hyphen);
            if (!isDotSeparatedIdentifiers(prerelease)) {
                return Optional.empty();
            }
        }

        String[] core = rest.split("\\.", -1);
        if (core.length != 3) {
            return Optional.empty();
        }
        int[] numbers = new int[3];
        for (int i = 0; i < 3; i++) {
            if (!isNumericIdentifier(core[i])) {
                return Optional.empty();
            }
            try {
                numbers[i] = Integer.parseInt(core[i]);
            } catch (NumberFormatException overflow) {
                return Optional.empty();
            }
        }
        return Optional.of(
                new SemanticVersion(numbers[0], numbers[1], numbers[2], prerelease, build));
    }

    public Optional<String> prereleaseIfAny() {
        return prerelease.isEmpty() ? Optional.empty() : Optional.of(prerelease);
    }

    public Optional<String> buildIfAny() {
        return build.isEmpty() ? Optional.empty() : Optional.of(build);
    }

    public boolean isPrerelease() {
        return !prerelease.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append(major).append('.').append(minor)
                .append('.').append(patch);
        if (!prerelease.isEmpty()) {
            sb.append('-').append(prerelease);
        }
        if (!build.isEmpty()) {
            sb.append('+').append(build);
        }
        return sb.toString();
    }

    /** SemVer 2.0 precedence. Build metadata is ignored, as the specification requires. */
    @Override
    public int compareTo(SemanticVersion other) {
        int byCore = Integer.compare(major, other.major);
        if (byCore != 0) {
            return byCore;
        }
        byCore = Integer.compare(minor, other.minor);
        if (byCore != 0) {
            return byCore;
        }
        byCore = Integer.compare(patch, other.patch);
        if (byCore != 0) {
            return byCore;
        }
        return comparePrerelease(prerelease, other.prerelease);
    }

    private static int comparePrerelease(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0;
        }
        // A release outranks any prerelease of the same core version.
        if (a.isEmpty()) {
            return 1;
        }
        if (b.isEmpty()) {
            return -1;
        }
        String[] left = a.split("\\.", -1);
        String[] right = b.split("\\.", -1);
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            int cmp = compareIdentifier(left[i], right[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    private static int compareIdentifier(String a, String b) {
        boolean an = isNumericIdentifier(a);
        boolean bn = isNumericIdentifier(b);
        if (an && bn) {
            // Compared as numbers, and via length first so that arbitrarily long identifiers do not
            // overflow. SemVer forbids leading zeroes, so length is a valid primary key.
            return a.length() != b.length()
                    ? Integer.compare(a.length(), b.length())
                    : a.compareTo(b);
        }
        if (an) {
            return -1; // numeric identifiers always sort below alphanumeric ones
        }
        if (bn) {
            return 1;
        }
        return a.compareTo(b); // ASCII order, per SemVer
    }

    private static boolean isNumericIdentifier(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return false;
            }
        }
        return s.length() == 1 || s.charAt(0) != '0'; // no leading zeroes
    }

    private static boolean isDotSeparatedIdentifiers(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (String part : s.split("\\.", -1)) {
            if (part.isEmpty()) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                boolean allowed = (c >= '0' && c <= '9')
                        || (c >= 'a' && c <= 'z')
                        || (c >= 'A' && c <= 'Z')
                        || c == '-';
                if (!allowed) {
                    return false;
                }
            }
        }
        return true;
    }
}
