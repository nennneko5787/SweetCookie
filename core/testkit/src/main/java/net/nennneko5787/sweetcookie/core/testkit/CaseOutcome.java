package net.nennneko5787.sweetcookie.core.testkit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.json.CanonicalJson;
import net.nennneko5787.sweetcookie.core.format.json.JsonArray;
import net.nennneko5787.sweetcookie.core.format.json.JsonBool;
import net.nennneko5787.sweetcookie.core.format.json.JsonObject;
import net.nennneko5787.sweetcookie.core.format.json.JsonString;
import net.nennneko5787.sweetcookie.core.format.json.JsonValue;

/**
 * What happened to one case, and the file the Gradle build reads to find out.
 *
 * <p>Without this, {@code specConformance} would have to take on faith that a case referenced by a
 * coverage entry actually ran — and this project has already shipped three checks that could not
 * fail. A case that was never executed is recorded as {@link Status#NO_RUNNER}, not omitted, so the
 * absence is visible rather than indistinguishable from success.
 */
@SpecImpl("SC-100")
public record CaseOutcome(String id, ConformanceCase.Tier tier, Status status, String detail) {

    public enum Status {
        PASSED,
        FAILED,
        /** Disabled by {@code skip:}, with a reason. */
        SKIPPED,
        /** Its tier has no runner yet. The loudest of the three non-passes. */
        NO_RUNNER,
    }

    public static CaseOutcome passed(ConformanceCase testCase) {
        return new CaseOutcome(testCase.id(), testCase.tier(), Status.PASSED, "");
    }

    public static CaseOutcome failed(ConformanceCase testCase, String detail) {
        return new CaseOutcome(testCase.id(), testCase.tier(), Status.FAILED, detail);
    }

    public static CaseOutcome skipped(ConformanceCase testCase) {
        return new CaseOutcome(testCase.id(), testCase.tier(), Status.SKIPPED,
                testCase.skip().orElse(""));
    }

    public static CaseOutcome noRunner(ConformanceCase testCase) {
        return new CaseOutcome(testCase.id(), testCase.tier(), Status.NO_RUNNER,
                "tier " + testCase.tier() + " has no runner yet");
    }

    /** Writes the results file {@code specConformance} reads. */
    public static void writeReport(Path file, List<CaseOutcome> outcomes) {
        List<JsonValue> entries = outcomes.stream().map(outcome -> {
            Map<String, JsonValue> node = new LinkedHashMap<>();
            node.put("id", new JsonString(outcome.id()));
            node.put("tier", new JsonString(outcome.tier().name()));
            node.put("status", new JsonString(outcome.status().name()));
            node.put("passed", JsonBool.of(outcome.status() == Status.PASSED));
            if (!outcome.detail().isEmpty()) {
                node.put("detail", new JsonString(outcome.detail()));
            }
            return (JsonValue) new JsonObject(node);
        }).toList();

        JsonObject report = new JsonObject(Map.of("cases", new JsonArray(entries)));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, CanonicalJson.pretty(report), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
