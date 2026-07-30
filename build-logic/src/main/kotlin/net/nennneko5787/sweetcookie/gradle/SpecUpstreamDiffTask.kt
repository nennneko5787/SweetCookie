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

        var checked = 0
        for (shard in shards) {
            val where = "spec/coverage/${shard.domain}.yaml"
            for (selector in shard.upstream) {
                val file = cache.resolve(selector.source)
                if (!file.isFile) {
                    problems.report(where, "declares `${selector.source}`, not in the fetched snapshot")
                    continue
                }

                val upstreamIds = extractIds(file, selector)

                // A selector that resolves to nothing is a DEFECT, not an empty result. The first
                // version of this task returned an empty list for an unresolvable pointer, so a
                // ledger missing 111 of 171 AI goals reported "covers every upstream identifier".
                // A check that cannot fail is worse than no check.
                if (upstreamIds.isEmpty()) {
                    problems.report(
                        where,
                        "selector ${selector.describe} resolved to no identifiers. " +
                            "The addressing is wrong, or upstream moved it - either way this shard " +
                            "is NOT being checked."
                    )
                    continue
                }

                checked += upstreamIds.size
                val missing = upstreamIds
                    .filterNot { it in known }
                    .filterNot { id -> allowlist.any { matches(id, it) } }
                for (id in missing.sorted()) {
                    problems.report(
                        where,
                        "upstream declares `$id`, which has no coverage entry. Add `status: stub`."
                    )
                }
            }
        }

        problems.failIfAny()
        logger.lifecycle(
            "specUpstreamDiff: $checked upstream identifier(s) all covered, at $commit."
        )
    }

    private fun extractIds(file: File, selector: UpstreamSelector): List<String> {
        val root = json.readTree(file)

        // doc_modules files are named `nodes[]` trees. An RFC 6901 pointer cannot select "the child
        // called AI Goals", so those shards address by node name instead.
        if (selector.nodePath.isNotEmpty()) {
            var node: JsonNode = root
            for (name in selector.nodePath) {
                val children = node["nodes"] ?: return emptyList()
                node = children.firstOrNull { it["name"]?.asText() == name } ?: return emptyList()
            }
            return (node["nodes"] ?: return emptyList())
                .mapNotNull { it["name"]?.asText() }
                .map { cleanDocName(it) }
        }

        val pointer = selector.pointer.orEmpty()
        val node: JsonNode = if (pointer.isBlank()) root else root.at(pointer)
        return when {
            node.isMissingNode -> emptyList()
            node.isArray && selector.idField != null -> node.mapNotNull { it[selector.idField]?.asText() }
            node.isArray -> node.mapNotNull { if (it.isTextual) it.asText() else it["name"]?.asText() }
            node.isObject -> node.fieldNames().asSequence().toList()
            else -> emptyList()
        }
    }

    /**
     * Strips the human-readable annotation Mojang appends to doc-tree node names.
     *
     * The `name` field is not a bare identifier. It reads
     * `minecraft:behavior.melee_attack (See JSON Schema since 1.26.0)`, and taking it verbatim
     * injects 554 entries whose ids contain prose. Found the hard way, twice.
     */
    private fun cleanDocName(raw: String): String =
        raw.substringBefore(" (").trim()

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
