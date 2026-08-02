package net.nennneko5787.lepus.core.testkit;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import org.yaml.snakeyaml.Yaml;

/**
 * One {@code spec/conformance/<domain>/<case>/case.yaml}. SC-000-adjacent; see
 * {@code spec/conformance/README.md} and {@code spec/schemas/conformance-case.schema.json}.
 *
 * <p>The schema is validated by {@code specValidate}, in the Gradle build, against the real JSON
 * Schema. This loader therefore does <b>not</b> re-validate: two validators drift, and the one that
 * runs less often becomes the one that is wrong. It reads what the schema has already guaranteed and
 * fails loudly on anything else.
 *
 * @param directory   the case directory on disk
 * @param id          {@code <domain>/<case>}, equal to the path
 * @param tier        the lowest tier that can prove the claim
 * @param spec        the normative document
 * @param features    Bedrock feature IDs this case proves; each must exist in a coverage shard
 * @param description what would break if this regressed
 * @param pack        the add-on under test
 * @param expect      golden files
 * @param assertions  inline assertions
 * @param diagnostics required and forbidden diagnostic codes
 * @param skip        why this case is disabled, when it is
 */
@SpecImpl("SC-100")
public record ConformanceCase(
        Path directory,
        String id,
        Tier tier,
        String spec,
        List<String> features,
        String description,
        PackSpec pack,
        Expectations expect,
        List<Assertion> assertions,
        DiagnosticExpectations diagnostics,
        Optional<String> skip) {

    /** {@code spec/conformance/README.md}'s tiers. Choose the lowest that can prove the claim. */
    public enum Tier {
        /** Parse only, no Minecraft. Runs in milliseconds, in this build. */
        T0,
        /** Translation, Molang, filters. No Minecraft, no world. */
        T1,
        /** Headless server behaviour. Needs a gametest harness. */
        T2,
        /** Rendering. Needs a graphical client. */
        T3,
        /** A human. */
        T4;

        /** True when a runner for this tier exists today. */
        public boolean hasRunner() {
            return this == T0;
        }
    }

    /**
     * The add-on under test.
     *
     * <p>{@code container} decides whether the materialised tree is handed to the loader as a
     * directory or zipped first. It exists because a case claiming to prove {@code container/mcaddon}
     * while being loaded from a directory proves nothing about {@code .mcaddon} — and a corpus that
     * over-claims is worse than a smaller one.
     */
    public record PackSpec(
            String dir, Container container, Optional<String> extendsFixture,
            List<String> loadOrder) {
    }

    /** How the pack tree reaches the loader. */
    public enum Container {
        DIRECTORY(""),
        MCPACK(".mcpack"),
        MCADDON(".mcaddon"),
        MCWORLD(".mcworld");

        private final String extension;

        Container(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }

        public boolean isArchive() {
            return this != DIRECTORY;
        }

        static Container parse(String raw) {
            return raw == null ? DIRECTORY : valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        }
    }

    /** Golden files, relative to the case directory. */
    public record Expectations(Optional<String> ir, Optional<String> diagnostics) {
    }

    /** One inline assertion. Exactly one of the predicates is present. */
    public record Assertion(
            String that,
            Optional<Object> equalTo,
            Optional<Object> contains,
            Optional<Object> notContains,
            Optional<String> matches,
            Optional<Boolean> absent) {
    }

    /**
     * Diagnostic codes that must and must not appear.
     *
     * <p>{@code forbidden} is the half worth using liberally: a feature that starts emitting a
     * warning it did not emit before is a regression, and it is exactly the kind that otherwise goes
     * unnoticed for months.
     */
    public record DiagnosticExpectations(
            List<String> expected, List<String> forbidden, boolean exhaustive) {
    }

    public boolean isSkipped() {
        return skip.isPresent();
    }

    /** The directory the case's pack tree lives in. */
    public Path packDirectory() {
        return directory.resolve(pack.dir());
    }

    /** Finds every {@code case.yaml} under {@code conformanceRoot}, sorted by id. */
    public static List<ConformanceCase> discover(Path conformanceRoot) throws IOException {
        try (var walk = Files.walk(conformanceRoot)) {
            List<Path> files = walk
                    .filter(path -> path.getFileName().toString().equals("case.yaml"))
                    .sorted()
                    .toList();
            List<ConformanceCase> cases = new ArrayList<>();
            for (Path file : files) {
                cases.add(load(file, conformanceRoot));
            }
            return cases;
        }
    }

    @SuppressWarnings("unchecked")
    public static ConformanceCase load(Path caseFile, Path conformanceRoot) throws IOException {
        Map<String, Object> root;
        try (Reader reader = Files.newBufferedReader(caseFile, StandardCharsets.UTF_8)) {
            root = new Yaml().load(reader);
        }
        if (root == null) {
            throw new IOException("empty case file: " + caseFile);
        }
        Path directory = caseFile.getParent();
        Map<String, Object> packNode = (Map<String, Object>) root.getOrDefault("pack", Map.of());
        Map<String, Object> expectNode = (Map<String, Object>) root.getOrDefault("expect", Map.of());
        Map<String, Object> diagNode =
                (Map<String, Object>) root.getOrDefault("diagnostics", Map.of());

        List<Assertion> assertions = new ArrayList<>();
        for (Object raw : (List<Object>) root.getOrDefault("assert", List.of())) {
            Map<String, Object> node = (Map<String, Object>) raw;
            assertions.add(new Assertion(
                    (String) node.get("that"),
                    Optional.ofNullable(node.get("equals")),
                    Optional.ofNullable(node.get("contains")),
                    Optional.ofNullable(node.get("notContains")),
                    Optional.ofNullable((String) node.get("matches")),
                    Optional.ofNullable((Boolean) node.get("absent"))));
        }

        return new ConformanceCase(
                directory,
                (String) root.get("id"),
                Tier.valueOf((String) root.get("tier")),
                (String) root.get("spec"),
                List.copyOf((List<String>) root.getOrDefault("features", List.of())),
                (String) root.get("description"),
                new PackSpec(
                        (String) packNode.getOrDefault("dir", "pack"),
                        Container.parse((String) packNode.get("container")),
                        Optional.ofNullable((String) packNode.get("extends")),
                        List.copyOf((List<String>) packNode.getOrDefault("loadOrder", List.of()))),
                new Expectations(
                        Optional.ofNullable((String) expectNode.get("ir")),
                        Optional.ofNullable((String) expectNode.get("diagnostics"))),
                List.copyOf(assertions),
                new DiagnosticExpectations(
                        List.copyOf((List<String>) diagNode.getOrDefault("expected", List.of())),
                        List.copyOf((List<String>) diagNode.getOrDefault("forbidden", List.of())),
                        Boolean.TRUE.equals(diagNode.get("exhaustive"))),
                Optional.ofNullable((String) root.get("skip")));
    }
}
