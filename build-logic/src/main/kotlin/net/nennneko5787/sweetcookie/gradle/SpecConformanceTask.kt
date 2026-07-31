package net.nennneko5787.sweetcookie.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

/**
 * Closes the two gaps between the coverage ledger and the conformance corpus.
 *
 * `specValidate` already checks that a case matches its schema and lives where its `id` says, and
 * `specLinks` already checks that an entry claiming `partial` or `implemented` names a case
 * directory that exists. Neither of them asks the two questions that decide whether the corpus is
 * telling the truth:
 *
 * 1. **Does a case's `features[]` name anything real?** A case may claim to prove a feature that has
 *    no coverage entry, and nothing notices. That is a claim with no counterparty.
 * 2. **Did the case actually run and pass?** An entry above `stub` pointing at a case that was never
 *    executed is indistinguishable, from the outside, from one that passes. This project has already
 *    shipped three checks that could not fail; the fix each time was to make "nothing happened" a
 *    failure rather than a silence.
 *
 * The results file is produced by `:testkit:test` in the `core` build, which this task depends on,
 * so it is always present. A missing file is a hard failure rather than a skip - the whole point is
 * that there is no path through here that reports success without evidence.
 */
abstract class SpecConformanceTask : DefaultTask() {

    @get:InputDirectory
    abstract val specDir: DirectoryProperty

    /** `core/testkit/build/conformance-results.json`, written by the corpus run. */
    @get:InputFile
    abstract val resultsFile: RegularFileProperty

    private val yaml = ObjectMapper(YAMLFactory())

    @TaskAction
    fun check() {
        val spec = specDir.get().asFile
        val problems = Problems("specConformance")

        val shards = CoverageLoader.loadAll(spec.resolve("coverage"))
        val entries = shards.flatMap { it.entries }
        val known = entries.map { it.spec to it.id }.toSet()

        val cases = loadCases(spec.resolve("conformance"), problems)

        // 1. Every feature a case claims to prove must exist, under that case's own document.
        for (case in cases) {
            for (feature in case.features) {
                if ((case.spec to feature) !in known) {
                    problems.report(
                        "spec/conformance/${case.id}/case.yaml",
                        "claims to prove `${case.spec}#$feature`, which has no coverage entry",
                    )
                }
            }
        }

        // 2. Every case a tracked entry relies on must have run and passed.
        val outcomes = readOutcomes(problems)
        val relied = entries.filter { it.claimsImplementation }
        for (entry in relied) {
            for (caseId in entry.conformance) {
                val where = "spec/coverage/${entry.shard}.yaml [${entry.id}]"
                when (val status = outcomes[caseId]) {
                    null -> problems.report(
                        where,
                        "relies on conformance case `$caseId`, which did not run at all",
                    )

                    "PASSED" -> Unit
                    else -> problems.report(
                        where,
                        "claims `${entry.status}` on conformance case `$caseId`, which is $status",
                    )
                }
            }
        }

        problems.failIfAny()

        // Cases nothing can execute yet are reported loudly on every run. A T2 case sitting in the
        // corpus with no harness looks exactly like a passing one unless something says otherwise.
        val stranded = outcomes.filterValues { it == "NO_RUNNER" }
        if (stranded.isNotEmpty()) {
            logger.warn(
                "specConformance: ${stranded.size} case(s) have no runner for their tier and were " +
                    "NOT executed: ${stranded.keys.sorted().joinToString(", ")}"
            )
        }
        val skipped = outcomes.filterValues { it == "SKIPPED" }
        if (skipped.isNotEmpty()) {
            logger.warn(
                "specConformance: ${skipped.size} case(s) are disabled by `skip:`: " +
                    skipped.keys.sorted().joinToString(", ")
            )
        }

        val passed = outcomes.count { it.value == "PASSED" }
        logger.lifecycle(
            "specConformance: $passed/${outcomes.size} case(s) passed, " +
                "${relied.sumOf { it.conformance.size }} ledger reference(s) all satisfied."
        )
    }

    private data class Case(val id: String, val spec: String, val features: List<String>)

    private fun loadCases(root: File, problems: Problems): List<Case> {
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.name == "case.yaml" }
            .mapNotNull { file ->
                val tree = runCatching { yaml.readTree(file) }.getOrElse {
                    problems.report(file.name, "unparseable YAML: ${it.message}")
                    return@mapNotNull null
                }
                Case(
                    id = tree["id"]?.asText().orEmpty(),
                    spec = tree["spec"]?.asText().orEmpty(),
                    features = tree["features"]?.map { it.asText() }.orEmpty(),
                )
            }
            .toList()
    }

    private fun readOutcomes(problems: Problems): Map<String, String> {
        val file = resultsFile.get().asFile
        if (!file.isFile) {
            problems.report(
                file.path,
                "the conformance results file is absent, so no case can be shown to have run. " +
                    "Run `./gradlew --project-dir core :testkit:test`.",
            )
            problems.failIfAny()
        }
        val root = ObjectMapper().readTree(file)
        return root["cases"].orEmpty().associate {
            it["id"].asText() to it["status"].asText()
        }
    }

    private fun com.fasterxml.jackson.databind.JsonNode?.orEmpty() =
        this ?: ObjectMapper().createArrayNode()
}
