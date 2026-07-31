package net.nennneko5787.sweetcookie.core.testkit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostic;
import net.nennneko5787.sweetcookie.core.format.json.CanonicalJson;
import net.nennneko5787.sweetcookie.core.format.json.JsonObject;
import net.nennneko5787.sweetcookie.core.format.json.JsonValue;
import net.nennneko5787.sweetcookie.core.format.pack.AddonLoader;
import net.nennneko5787.sweetcookie.core.format.pack.LoadOptions;
import net.nennneko5787.sweetcookie.core.format.pack.LoadedAddon;
import net.nennneko5787.sweetcookie.core.format.pack.LoadedAddonJson;

/**
 * Runs a T0 case: load the pack, compare the parse result and the diagnostics. No Minecraft.
 *
 * <p>The pack is <b>materialised into a temporary directory</b> — the fixture first, then the case's
 * own files over it — rather than being composed as a layered VFS. Two reasons: it is what the
 * user's disk actually looks like, so the case exercises discovery for real; and it keeps
 * {@code AddonLoader}'s public surface to "here are some paths", which is what the runtime will call
 * it with.
 *
 * <p>Every path that reaches a golden is rewritten relative to that temporary directory and has its
 * separators normalised. A golden holding {@code C:\Users\…\Temp\junit123\pack} would fail on every
 * machine except the one that wrote it, which is the standard way a golden corpus dies.
 */
@SpecImpl("SC-100")
public final class T0Runner {

    private final Path fixturesRoot;

    public T0Runner(Path conformanceRoot) {
        this.fixturesRoot = conformanceRoot.resolve("_fixtures");
    }

    /** Runs {@code testCase}, throwing {@link AssertionError} with a readable report on failure. */
    public void run(ConformanceCase testCase, Path workingDirectory) throws IOException {
        // Both forms are named `pack`, so a golden reads `pack!behaviour` or `pack.mcaddon!behaviour`
        // and never mentions a directory the runner invented.
        Path tree = materialise(testCase, workingDirectory.resolve("pack"));
        Path packRoot = testCase.pack().container().isArchive()
                ? zip(tree, workingDirectory.resolve("pack" + testCase.pack().container().extension()))
                : tree;
        UnaryOperator<String> rewrite = relativiseTo(workingDirectory);

        LoadOptions options = LoadOptions.DEFAULT;
        List<String> problems = new ArrayList<>();

        try (LoadedAddon addon = AddonLoader.load(List.of(packRoot), options)) {
            JsonObject ir = LoadedAddonJson.of(addon, rewrite);
            JsonValue diagnostics = LoadedAddonJson.diagnostics(addon.diagnostics(), rewrite);
            JsonValue root = new JsonObject(Map.of("ir", ir, "diagnostics", diagnostics));

            checkDiagnostics(testCase, addon, problems);
            checkAssertions(testCase, root, problems);

            testCase.expect().ir().ifPresent(name ->
                    Goldens.compare(testCase.directory().resolve(name), ir).ifPresent(problems::add));
            testCase.expect().diagnostics().ifPresent(name ->
                    Goldens.compare(testCase.directory().resolve(name), diagnostics)
                            .ifPresent(problems::add));
        }

        if (!problems.isEmpty()) {
            throw new AssertionError(testCase.id() + "\n  " + String.join("\n  ", problems));
        }
    }

    private void checkDiagnostics(
            ConformanceCase testCase, LoadedAddon addon, List<String> problems) {
        Set<String> emitted = new LinkedHashSet<>();
        for (Diagnostic diagnostic : addon.diagnostics().diagnostics()) {
            emitted.add(diagnostic.codeString());
        }
        for (String code : testCase.diagnostics().expected()) {
            if (!emitted.contains(code)) {
                problems.add("expected diagnostic " + code + " was not emitted; got " + emitted);
            }
        }
        for (String code : testCase.diagnostics().forbidden()) {
            if (emitted.contains(code)) {
                problems.add("forbidden diagnostic " + code + " was emitted");
            }
        }
        if (testCase.diagnostics().exhaustive()) {
            Set<String> unexpected = new LinkedHashSet<>(emitted);
            testCase.diagnostics().expected().forEach(unexpected::remove);
            if (!unexpected.isEmpty()) {
                // A feature that starts emitting a warning it did not emit before is a regression,
                // and it is exactly the kind that otherwise goes unnoticed for months.
                problems.add("unlisted diagnostics under `exhaustive: true`: " + unexpected);
            }
        }
    }

    private void checkAssertions(
            ConformanceCase testCase, JsonValue root, List<String> problems) {
        for (ConformanceCase.Assertion assertion : testCase.assertions()) {
            Optional<JsonValue> found;
            try {
                found = JsonPath.resolve(root, assertion.that());
            } catch (IllegalArgumentException malformed) {
                // A typo in a case file is a broken test, not a failing one.
                throw new IllegalArgumentException(
                        testCase.id() + ": " + malformed.getMessage(), malformed);
            }

            if (assertion.absent().isPresent()) {
                boolean shouldBeAbsent = assertion.absent().get();
                if (shouldBeAbsent != found.isEmpty()) {
                    problems.add(assertion.that() + ": expected absent=" + shouldBeAbsent
                            + ", was absent=" + found.isEmpty());
                }
                continue;
            }
            if (found.isEmpty()) {
                problems.add(assertion.that() + ": addresses nothing");
                continue;
            }
            JsonValue value = found.get();
            String rendered = plain(value);

            assertion.equalTo().ifPresent(expected -> {
                if (!rendered.equals(String.valueOf(expected))) {
                    problems.add(assertion.that() + ": expected `" + expected + "`, was `"
                            + rendered + "`");
                }
            });
            assertion.contains().ifPresent(needle -> {
                String text = String.valueOf(needle);
                if (!contains(value, rendered, text)) {
                    problems.add(assertion.that() + ": does not contain `" + text + "`");
                }
            });
            assertion.notContains().ifPresent(needle -> {
                String text = String.valueOf(needle);
                if (contains(value, rendered, text)) {
                    problems.add(assertion.that() + ": must not contain `" + text + "`, but does");
                }
            });
            assertion.matches().ifPresent(pattern -> {
                if (!rendered.matches(pattern)) {
                    problems.add(assertion.that() + ": `" + rendered + "` does not match /"
                            + pattern + "/");
                }
            });
        }
    }

    /**
     * Membership for an array, substring for anything else.
     *
     * <p>An array is checked element-wise rather than by substring, so that
     * {@code contains: textures/a.png} cannot be satisfied by
     * {@code subpacks/hd/textures/a.png} — which would make the negative form useless exactly where
     * it is most needed.
     */
    private static boolean contains(JsonValue value, String rendered, String needle) {
        return value.asArray()
                .map(array -> array.values().stream()
                        .anyMatch(element -> plain(element).equals(needle)))
                .orElseGet(() -> rendered.contains(needle));
    }

    /** A scalar as a case author would write it — an unquoted string, not JSON. */
    private static String plain(JsonValue value) {
        return value.asString().orElseGet(() -> CanonicalJson.write(value));
    }

    private Path materialise(ConformanceCase testCase, Path into) throws IOException {
        Files.createDirectories(into);
        Optional<String> fixture = testCase.pack().extendsFixture();
        if (fixture.isPresent()) {
            Path source = fixturesRoot.resolve(fixture.get());
            if (!Files.isDirectory(source)) {
                throw new IOException(
                        testCase.id() + " extends fixture `" + fixture.get() + "`, which is absent");
            }
            copyTree(source, into);
        }
        Path packDirectory = testCase.packDirectory();
        if (Files.isDirectory(packDirectory)) {
            copyTree(packDirectory, into);
        } else if (fixture.isEmpty()) {
            throw new IOException(testCase.id() + " has no pack directory at " + packDirectory);
        }
        return into;
    }

    /**
     * Zips a materialised tree, so a container case exercises the container it names.
     *
     * <p>Entries are added in sorted order. A zip's entry order is part of what SC-100 §3 resolves
     * case collisions by, so leaving it to {@code Files.walk} would make a case pass or fail
     * depending on the filesystem it was built on.
     */
    private static Path zip(Path tree, Path archive) throws IOException {
        List<Path> files;
        try (var walk = Files.walk(tree)) {
            files = walk.filter(Files::isRegularFile)
                    .sorted(java.util.Comparator.comparing(p -> tree.relativize(p).toString()
                            .replace('\\', '/')))
                    .toList();
        }
        Files.createDirectories(archive.getParent());
        try (var out = new java.util.zip.ZipOutputStream(
                Files.newOutputStream(archive), java.nio.charset.StandardCharsets.UTF_8)) {
            for (Path file : files) {
                String name = tree.relativize(file).toString().replace('\\', '/');
                out.putNextEntry(new java.util.zip.ZipEntry(name));
                out.write(Files.readAllBytes(file));
                out.closeEntry();
            }
        }
        return archive;
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (var walk = Files.walk(from)) {
            walk.forEach(source -> {
                Path target = to.resolve(from.relativize(source).toString());
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private static UnaryOperator<String> relativiseTo(Path root) {
        String prefix = root.toString();
        return path -> {
            String out = path.startsWith(prefix) ? path.substring(prefix.length()) : path;
            out = out.replace('\\', '/');
            while (out.startsWith("/")) {
                out = out.substring(1);
            }
            return out.isEmpty() ? "." : out;
        };
    }
}
