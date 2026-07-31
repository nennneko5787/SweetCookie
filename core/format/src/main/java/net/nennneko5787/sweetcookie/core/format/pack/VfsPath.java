package net.nennneko5787.sweetcookie.core.format.pack;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * Path normalisation and the safety checks on an archive entry name. SC-100 §3.
 *
 * <p>An add-on is untrusted input (constitution rule 1, SC-260), and an archive entry name is the
 * cheapest attack surface it has. Every rule here corresponds to a real technique rather than to
 * defensiveness in the abstract.
 *
 * <p>{@link #inspect} takes the <b>raw</b> name rather than a normalised one, deliberately. An
 * earlier shape normalised first and checked afterwards, which quietly turned {@code /etc/passwd}
 * into the harmless-looking {@code etc/passwd} and reported nothing — the entry was then accepted
 * under a name it had never declared. A check must see what the archive actually said.
 */
@SpecImpl("SC-100")
public final class VfsPath {

    /** Why an entry name was refused. Each maps to a diagnostic in {@code FormatDiagnostics}. */
    public enum Rejection {
        /** Absolute, drive-lettered, or containing a {@code ..} segment — zip-slip. */
        ESCAPES_ROOT,
        /** Longer than the limit after normalisation. */
        TOO_LONG,
        /** Not valid UTF-8, or not its own NFC normalisation. */
        NOT_NORMALISED,
    }

    /** The outcome of {@link #inspect}: exactly one of {@code path} and {@code rejection} is set. */
    public record Inspection(String path, Optional<Rejection> rejection) {
        public boolean accepted() {
            return rejection.isEmpty();
        }
    }

    private static final char REPLACEMENT = (char) 0xFFFD;

    private VfsPath() {
    }

    /**
     * Normalises separators and removes empty and {@code .} segments, preserving case.
     *
     * <p>Backslashes become slashes: archives written by Windows tooling contain them, and treating
     * {@code textures\blocks\a.png} as one long file name rather than a path is how a pack ends up
     * looking empty.
     */
    public static String normalise(String raw) {
        String slashed = raw.replace('\\', '/');
        StringBuilder sb = new StringBuilder(slashed.length());
        for (String segment : slashed.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('/');
            }
            sb.append(segment);
        }
        return sb.toString();
    }

    /** The lookup key for a normalised path: lowercase under {@code Locale.ROOT} (SC-000 §9). */
    public static String key(String normalised) {
        return normalised.toLowerCase(Locale.ROOT);
    }

    /** {@link #normalise} then {@link #key}, for callers that already trust the path. */
    public static String normalisedKey(String raw) {
        return key(normalise(raw));
    }

    /**
     * Checks a raw archive entry name and normalises it.
     *
     * @param raw       the entry name exactly as the archive declared it
     * @param maxLength the path-length limit, SC-100 §3
     */
    public static Inspection inspect(String raw, int maxLength) {
        String slashed = raw.replace('\\', '/');

        if (slashed.startsWith("/") || isDriveRooted(slashed)) {
            return rejected(Rejection.ESCAPES_ROOT);
        }
        for (String segment : slashed.split("/", -1)) {
            if (segment.equals("..")) {
                return rejected(Rejection.ESCAPES_ROOT);
            }
        }

        String normalised = normalise(slashed);
        if (normalised.isEmpty()) {
            return rejected(Rejection.ESCAPES_ROOT);
        }
        if (normalised.length() > maxLength) {
            return rejected(Rejection.TOO_LONG);
        }
        // U+FFFD means the name was not decodable as UTF-8 and the decoder substituted. Comparing
        // against NFC catches the other half: two names a filesystem folds together but a string
        // comparison does not, which is how a second entry gets written over an already-checked one.
        if (normalised.indexOf(REPLACEMENT) >= 0
                || !Normalizer.isNormalized(normalised, Normalizer.Form.NFC)) {
            return rejected(Rejection.NOT_NORMALISED);
        }
        return new Inspection(normalised, Optional.empty());
    }

    private static boolean isDriveRooted(String slashed) {
        return slashed.length() >= 2
                && slashed.charAt(1) == ':'
                && Character.isLetter(slashed.charAt(0));
    }

    private static Inspection rejected(Rejection why) {
        return new Inspection("", Optional.of(why));
    }

    /** The directory part of a normalised path, or the empty string for a root-level entry. */
    public static String parent(String normalised) {
        int slash = normalised.lastIndexOf('/');
        return slash < 0 ? "" : normalised.substring(0, slash);
    }

    /** The last segment of a normalised path. */
    public static String fileName(String normalised) {
        int slash = normalised.lastIndexOf('/');
        return slash < 0 ? normalised : normalised.substring(slash + 1);
    }

    /** The lowercase extension without the dot, or the empty string. */
    public static String extension(String normalised) {
        String name = fileName(normalised);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** True when {@code path} is inside {@code directory}, at any depth. Both normalised keys. */
    public static boolean isUnder(String path, String directory) {
        return directory.isEmpty() || path.startsWith(directory + "/");
    }
}
