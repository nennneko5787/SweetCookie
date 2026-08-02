package net.nennneko5787.lepus.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SchemaValidatorsConfig
import com.networknt.schema.SpecVersion
import java.io.File
import java.util.Locale
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Validates the `spec/coverage` shards and the `spec/conformance` cases against their schemas, and
 * enforces the ledger rules that a schema cannot express.
 *
 * Constitution rule 9: the ledger is checked, not trusted. A compatibility table nobody verifies is
 * worse than none, because it turns user bug reports into arguments.
 */
abstract class SpecValidateTask : DefaultTask() {

    @get:InputDirectory
    abstract val specDir: DirectoryProperty

    private val yaml = ObjectMapper(YAMLFactory())
    private val json = ObjectMapper()

    /**
     * The validator localises its messages to the JVM's default locale, which on a Japanese machine
     * emits Japanese schema errors into a repository whose output policy is English (constitution
     * rule 11) - and into whatever a user pastes into an issue or a search engine.
     */
    private val schemaConfig: SchemaValidatorsConfig =
        SchemaValidatorsConfig.builder().locale(Locale.ENGLISH).build()

    @TaskAction
    fun validate() {
        val spec = specDir.get().asFile
        val problems = Problems("specValidate")

        validateCoverage(spec, problems)
        validateConformance(spec, problems)

        problems.failIfAny()
        logger.lifecycle("specValidate: ledger is internally consistent.")
    }

    private fun validateCoverage(spec: File, problems: Problems) {
        val schemaFile = spec.resolve("schemas/coverage.schema.json")
        if (!schemaFile.isFile) {
            problems.report("spec/schemas", "coverage.schema.json is missing")
            return
        }
        val schema = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(schemaFile.inputStream(), schemaConfig)

        val coverageDir = spec.resolve("coverage")
        val shardFiles = coverageDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "yaml" && !it.name.startsWith("_") }

        if (shardFiles.isEmpty()) {
            problems.report("spec/coverage", "no shards found")
            return
        }

        // Keyed by (spec, id), NOT by id alone. Bedrock reuses component names across domains with
        // different semantics - minecraft:collision_box is both an entity component and a block
        // component, and minecraft:loot, transformation, display_name, custom_components, projectile
        // and tags are all similarly overloaded. The specification document disambiguates them,
        // which is why @SpecImpl is written `SC-150#minecraft:collision_box`. See spec/ids.md.
        val seenIds = mutableMapOf<Pair<String, String>, String>()

        for (file in shardFiles) {
            val where = "spec/coverage/${file.name}"

            // Schema first. A malformed shard cannot be checked semantically.
            val tree = runCatching { yaml.readTree(file) }.getOrElse {
                problems.report(where, "unparseable YAML: ${it.message}")
                continue
            }
            val asJson = json.readTree(json.writeValueAsBytes(tree))
            schema.validate(asJson).forEach { problems.report(where, it.message) }

            // The shard's `domain` must match its filename, or a report generated from the ledger
            // silently attributes entries to the wrong page.
            val domain = tree["domain"]?.asText()
            if (domain != file.nameWithoutExtension) {
                problems.report(where, "`domain: $domain` does not match the file name")
            }

            val shard = runCatching { CoverageLoader.load(file) }.getOrElse {
                problems.report(where, it.message ?: "failed to load")
                continue
            }

            for (entry in shard.entries) {
                val at = "$where [${entry.id}]"

                // The same identifier under the same specification in two places means two shards
                // claim to own it, and a status change in one would not be visible from the other.
                seenIds.put(shard.spec to entry.id, shard.domain)?.let { previous ->
                    problems.report(
                        at,
                        if (previous == shard.domain) "declared twice in the same shard"
                        else "already declared in `$previous` under the same spec (${shard.spec})"
                    )
                }

                // The rules that govern MEANING. They live here rather than in the schema on
                // purpose: expressing them in both places reported every violation twice, once as
                // readable prose and once as an unreadable `allOf[2].then.not` path. SC-000
                // section 2 draws the line - the schema governs shape, this governs meaning.
                if (entry.status == "implemented" && entry.conformance.isEmpty()) {
                    problems.report(at, "status `implemented` with no conformance case")
                }
                // ADR-0011: `implemented` is written by a human and verified here. These two rules
                // are what make it mean what SC-000 section 3 says rather than what its author
                // hoped. A fidelity note states an observable difference from Bedrock, so an entry
                // claiming there is none cannot carry one; and a `fields` map holding anything but
                // `ok` is an enumerated divergence in table form, which says `partial` whatever the
                // status line says.
                if (entry.status == "implemented" && entry.fidelity != null) {
                    problems.report(
                        at,
                        "status `implemented` carries a `fidelity` note, which describes a " +
                            "divergence. Either the note is stale or the status is `partial`."
                    )
                }
                if (entry.status == "implemented") {
                    val divergent = entry.fields.filterValues { it != "ok" }
                    if (divergent.isNotEmpty()) {
                        problems.report(
                            at,
                            "status `implemented` with non-`ok` field(s) " +
                                divergent.keys.sorted().joinToString(", ") +
                                ". An enumerated divergence is `partial`."
                        )
                    }
                }
                if (entry.claimsImplementation && entry.impl == null) {
                    problems.report(at, "status `${entry.status}` names no implementation class")
                }
                if (entry.status == "stub" && entry.impl != null) {
                    problems.report(at, "status `stub` must not name an implementation class")
                }
                if (entry.status in setOf("partial", "unsupported", "wontfix")) {
                    val fidelity = entry.fidelity
                    when {
                        fidelity == null ->
                            problems.report(at, "status `${entry.status}` requires a `fidelity` note")
                        fidelity.length < 40 ->
                            problems.report(at, "`fidelity` is ${fidelity.length} chars; 40 minimum")
                        looksLikeAnExcuse(fidelity) ->
                            problems.report(
                                at,
                                "`fidelity` must describe an OBSERVABLE difference, not progress. " +
                                    "See spec/process.md section 4."
                            )
                    }
                }
                entry.diagnostics.forEach {
                    if (!it.matches(Regex("^SCE-\\d{4}$"))) {
                        problems.report(at, "malformed diagnostic code `$it`")
                    }
                }
            }
        }
    }

    /**
     * Catches the failure mode the `fidelity` field exists to prevent: a note that says the work is
     * unfinished instead of saying what a player would see. "Not implemented yet" padded to forty
     * characters passes a length check and tells a user nothing.
     */
    private fun looksLikeAnExcuse(text: String): Boolean {
        val t = text.lowercase()
        val excuses = listOf(
            "not implemented yet", "todo", "not done", "coming soon",
            "will be implemented", "needs work", "wip", "no implementation yet",
        )
        return excuses.any { t.contains(it) }
    }

    private fun validateConformance(spec: File, problems: Problems) {
        val schemaFile = spec.resolve("schemas/conformance-case.schema.json")
        if (!schemaFile.isFile) {
            problems.report("spec/schemas", "conformance-case.schema.json is missing")
            return
        }
        val schema = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(schemaFile.inputStream(), schemaConfig)

        val conformanceDir = spec.resolve("conformance")
        if (!conformanceDir.isDirectory) return

        conformanceDir.walkTopDown()
            .filter { it.isFile && it.name == "case.yaml" }
            .forEach { file ->
                val rel = file.relativeTo(spec).invariantSeparatorsPath
                val tree = runCatching { yaml.readTree(file) }.getOrElse {
                    problems.report(rel, "unparseable YAML: ${it.message}")
                    return@forEach
                }
                val asJson = json.readTree(json.writeValueAsBytes(tree))
                schema.validate(asJson).forEach { problems.report(rel, it.message) }

                // The declared id must match where the case actually lives, or the coverage links
                // point at nothing.
                val expected = file.parentFile.relativeTo(conformanceDir).invariantSeparatorsPath
                val declared = tree["id"]?.asText()
                if (declared != expected) {
                    problems.report(rel, "`id: $declared` does not match its path `$expected`")
                }

                // A skipped case is not a failure, but it must stay visible: the whole point of the
                // corpus is that nothing quietly stops being checked.
                tree["skip"]?.asText()?.let {
                    logger.warn("specValidate: SKIPPED $expected - $it")
                }
            }
    }
}
