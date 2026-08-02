package net.nennneko5787.lepus.core.testkit;

import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the conformance corpus. {@code spec/conformance/README.md}.
 *
 * <p>Every {@code case.yaml} in the repository is discovered and either run, skipped with its stated
 * reason, or recorded as having <b>no runner for its tier</b>. That third outcome is the one this
 * class exists to make loud: a T2 case sitting in the corpus with nothing to execute it looks
 * exactly like a passing case unless something says otherwise, and this project has already shipped
 * three checks that could not fail.
 *
 * <p>The results are written to a file that {@code specConformance} reads, so the ledger's claim
 * that a case proves an entry is checked against whether the case ran at all.
 */
class ConformanceCorpusTest {

    private static final List<CaseOutcome> OUTCOMES = Collections.synchronizedList(new ArrayList<>());

    @TestFactory
    @ProvesSpec("SC-100")
    Stream<DynamicTest> corpus(@org.junit.jupiter.api.io.TempDir Path work) throws IOException {
        Path specDir = specDirectory();
        Path conformance = specDir.resolve("conformance");
        if (!Files.isDirectory(conformance)) {
            return Stream.of(dynamicTest("corpus is absent", () ->
                    abort("spec/conformance is not present at " + conformance)));
        }

        T0Runner runner = new T0Runner(conformance);
        List<ConformanceCase> cases = ConformanceCase.discover(conformance);

        return cases.stream().map(testCase -> dynamicTest(testCase.id(), () -> {
            if (testCase.isSkipped()) {
                OUTCOMES.add(CaseOutcome.skipped(testCase));
                abort("skipped: " + testCase.skip().orElseThrow());
            }
            if (!testCase.tier().hasRunner()) {
                OUTCOMES.add(CaseOutcome.noRunner(testCase));
                abort("tier " + testCase.tier() + " has no runner yet");
            }
            Path directory = work.resolve(testCase.id().replace('/', '_'));
            try {
                runner.run(testCase, directory);
                OUTCOMES.add(CaseOutcome.passed(testCase));
            } catch (AssertionError | RuntimeException | IOException failure) {
                OUTCOMES.add(CaseOutcome.failed(testCase, String.valueOf(failure.getMessage())));
                throw failure;
            }
        }));
    }

    @AfterAll
    static void writeReport() {
        String target = System.getProperty("lepus.conformanceResults");
        if (target == null) {
            return;
        }
        List<CaseOutcome> sorted = new ArrayList<>(OUTCOMES);
        sorted.sort(java.util.Comparator.comparing(CaseOutcome::id));
        CaseOutcome.writeReport(Path.of(target), sorted);
    }

    private static Path specDirectory() {
        String configured = System.getProperty("lepus.specDir");
        if (configured != null) {
            return Path.of(configured);
        }
        // Fallback for an IDE run that did not pick up the Gradle system properties.
        return Path.of("").toAbsolutePath().getParent().getParent().resolve("spec");
    }
}
