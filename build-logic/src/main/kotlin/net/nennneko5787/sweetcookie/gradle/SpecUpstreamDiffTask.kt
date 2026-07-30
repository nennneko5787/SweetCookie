package net.nennneko5787.sweetcookie.gradle

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build when Mojang publishes a feature identifier the ledger does not know about.
 *
 * This is the half of constitution rule 9 that points outward: `specLinks` stops the ledger from
 * over-claiming, and this stops it from silently going out of date. A Bedrock update becomes a
 * concrete, enumerated list of new work rather than a slow drift into inaccuracy.
 *
 * The correct response to a failure here is to add a `status: stub` coverage entry - not to
 * implement the feature, and not to silence the check. Genuine exclusions go in
 * `spec/upstream/allowlist-missing.yaml` with a reason.
 *
 * Nothing from `Mojang/bedrock-samples` is committed (constitution rule 10). When the fetched cache
 * is absent this task SKIPS with an explanation rather than failing, so a fresh clone still builds
 * offline; CI fetches first, which is where the check has teeth.
 */
abstract class SpecUpstreamDiffTask : DefaultTask() {

    @get:InputDirectory
    abstract val specDir: DirectoryProperty

    private val json = ObjectMapper()

    @TaskAction
    fun diff() {
        val spec = specDir.get().asFile
        val lock = spec.resolve("upstream/bedrock-samples.lock.json")

        if (!lock.isFile) {
            logger.warn("specUpstreamDiff: no lock file; skipping.")
            return
        }
        val lockNode = json.readTree(lock)
        val commit = lockNode["commit"]?.asText().orEmpty()
        if (commit.isBlank() || commit.all { it == '0' }) {
            logger.warn(
                "specUpstreamDiff: SKIPPED - the upstream snapshot is not pinned yet.\n" +
                    "  Run `./gradlew updateUpstreamLock` to pin one. Until then the ledger cannot be\n" +
                    "  checked against Mojang's own feature lists, so newly added Bedrock features will\n" +
                    "  NOT be detected. See spec/upstream/fetch.md."
            )
            return
        }

        val cache = project.rootDir.resolve(".upstream-cache")
        if (!cache.isDirectory) {
            logger.warn(
                "specUpstreamDiff: SKIPPED - .upstream-cache/ is absent. " +
                    "Run `./gradlew fetchUpstreamMetadata` first."
            )
            return
        }

        val allowlist = loadAllowlist(spec.resolve("upstream/allowlist-missing.yaml"))
        val shards = CoverageLoader.loadAll(spec.resolve("coverage"))
        val known = shards.flatMap { it.entries }.map { it.id }.toSet()
        val problems = Problems("specUpstreamDiff")

        for (shard in shards) {
            val source = shard.upstreamSource ?: continue
            val file = cache.resolve(source)
            if (!file.isFile) {
                problems.report(
                    "spec/coverage/${shard.domain}.yaml",
                    "declares upstream source `$source`, which is not in the fetched snapshot"
                )
                continue
            }
            val upstreamIds = extractIds(file, shard.upstreamPointer.orEmpty(), shard.upstreamIdField)
            val missing = upstreamIds
                .filterNot { it in known }
                .filterNot { id -> allowlist.any { matches(id, it) } }
            for (id in missing.sorted()) {
                problems.report(
                    "spec/coverage/${shard.domain}.yaml",
                    "upstream declares `$id`, which has no coverage entry. " +
                        "Add it with `status: stub`."
                )
            }
        }

        problems.failIfAny()
        logger.lifecycle("specUpstreamDiff: ledger covers every upstream identifier at $commit.")
    }

    private fun extractIds(file: File, pointer: String, idField: String?): List<String> {
        val root = json.readTree(file)
        val node: JsonNode = if (pointer.isBlank()) root else root.at(pointer)
        return when {
            node.isMissingNode -> emptyList()
            node.isObject -> node.fieldNames().asSequence().toList()
            node.isArray && idField != null -> node.mapNotNull { it[idField]?.asText() }
            node.isArray -> node.mapNotNull { if (it.isTextual) it.asText() else it["name"]?.asText() }
            else -> emptyList()
        }
    }

    /** Entries may use a trailing `*` wildcard, e.g. `minecraft:editor_*`. */
    private fun matches(id: String, pattern: String): Boolean =
        if (pattern.endsWith("*")) id.startsWith(pattern.dropLast(1)) else id == pattern

    private fun loadAllowlist(file: File): List<String> {
        if (!file.isFile) return emptyList()
        return Regex("^\\s*-\\s+id:\\s*\"?([^\"\\n]+?)\"?\\s*$", RegexOption.MULTILINE)
            .findAll(file.readText())
            .map { it.groupValues[1].trim() }
            .toList()
    }
}
