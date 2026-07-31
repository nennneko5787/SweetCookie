package net.nennneko5787.sweetcookie.core.testkit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.json.CanonicalJson;
import net.nennneko5787.sweetcookie.core.format.json.Json;
import net.nennneko5787.sweetcookie.core.format.json.JsonValue;

/**
 * Golden files: read, compare, and — only when explicitly asked — rewrite.
 *
 * <p>Comparison canonicalises both sides first (SC-000 §6), so reformatting a golden by hand can
 * never fail a case while a changed value always does. The file on disk is the indented form,
 * because a one-line golden makes {@code git diff} useless and reviewing the diff is the entire
 * point.
 *
 * <p>Acceptance is behind {@code -Dsweetcookie.accept=true} and is never automatic. A golden
 * accepted without being read is worse than no golden at all: it converts a future regression into
 * a green build.
 */
@SpecImpl("SC-100")
public final class Goldens {

    private static final String ACCEPT_PROPERTY = "sweetcookie.accept";

    private Goldens() {
    }

    /** True when this run is regenerating goldens rather than checking them. */
    public static boolean accepting() {
        return Boolean.parseBoolean(System.getProperty(ACCEPT_PROPERTY, "false"));
    }

    /**
     * Compares {@code actual} against the golden at {@code file}.
     *
     * @return empty when they match, or a human-readable explanation of the first difference
     */
    public static Optional<String> compare(Path file, JsonValue actual) {
        if (accepting()) {
            write(file, actual);
            return Optional.empty();
        }
        if (!Files.isRegularFile(file)) {
            return Optional.of(
                    "the golden " + file.getFileName() + " does not exist. Generate it with\n"
                            + "    ./gradlew --project-dir core :testkit:test -D" + ACCEPT_PROPERTY
                            + "=true\n"
                            + "and READ the diff before committing it.\nActual:\n"
                            + CanonicalJson.pretty(actual));
        }
        String expectedText;
        try {
            expectedText = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        JsonValue expected;
        try {
            expected = Json.parse(expectedText);
        } catch (RuntimeException malformed) {
            return Optional.of("the golden " + file.getFileName() + " is not valid JSON: "
                    + malformed.getMessage());
        }
        String expectedCanonical = CanonicalJson.write(expected);
        String actualCanonical = CanonicalJson.write(actual);
        if (expectedCanonical.equals(actualCanonical)) {
            return Optional.empty();
        }
        return Optional.of(describe(file, CanonicalJson.pretty(expected),
                CanonicalJson.pretty(actual)));
    }

    /** Writes the indented form, creating parent directories. */
    public static void write(Path file, JsonValue value) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, CanonicalJson.pretty(value), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The first differing line, with context.
     *
     * <p>Whole-file dumps are unreadable once a golden is more than a screen long, and a golden that
     * nobody reads on failure is a golden nobody trusts.
     */
    private static String describe(Path file, String expected, String actual) {
        List<String> left = expected.lines().toList();
        List<String> right = actual.lines().toList();
        int at = 0;
        while (at < left.size() && at < right.size() && left.get(at).equals(right.get(at))) {
            at++;
        }
        StringBuilder sb = new StringBuilder("golden mismatch: ")
                .append(file.getFileName()).append("\n");
        int from = Math.max(0, at - 3);
        for (int i = from; i < at; i++) {
            sb.append("      ").append(left.get(i)).append('\n');
        }
        sb.append("  expected: ").append(at < left.size() ? left.get(at) : "<end of file>")
                .append('\n');
        sb.append("  actual:   ").append(at < right.size() ? right.get(at) : "<end of file>")
                .append('\n');
        sb.append("  (line ").append(at + 1).append("; regenerate with -D")
                .append(ACCEPT_PROPERTY).append("=true and read the diff)");
        return sb.toString();
    }
}
