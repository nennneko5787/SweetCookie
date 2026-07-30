package net.nennneko5787.sweetcookie.gradle

import java.io.File
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml

/**
 * The in-memory form of the `spec/coverage` shards.
 *
 * Note for anyone editing the docs here: Kotlin block comments NEST, so a glob written as
 * `coverage` followed by two asterisks opens a nested comment and swallows the rest of the file.
 * Spell directory paths out instead.
 *
 * Deliberately permissive: this loader's job is to get the ledger into memory so the checks can
 * report *every* problem in one run. Rejecting a file here would hide the rest of its errors behind
 * the first one, and a contributor fixing twelve entries one build at a time gives up.
 */
data class CoverageEntry(
    val id: String,
    val status: String,
    val impl: String?,
    val fidelity: String?,
    val conformance: List<String>,
    val fields: Map<String, String>,
    val diagnostics: List<String>,
    val notes: String?,
    val shard: String,
    val spec: String,
) {
    val isTracked: Boolean get() = status != "stub"
    val claimsImplementation: Boolean get() = status == "implemented" || status == "partial"
}

data class CoverageShard(
    val file: File,
    val domain: String,
    val spec: String,
    val upstreamSource: String?,
    val upstreamPointer: String?,
    val upstreamIdField: String?,
    val entries: List<CoverageEntry>,
)

object CoverageLoader {

    private val validStatuses = setOf("stub", "partial", "implemented", "unsupported", "wontfix")

    fun loadAll(coverageDir: File): List<CoverageShard> =
        coverageDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "yaml" && !it.name.startsWith("_") }
            .sortedBy { it.name }
            .map { load(it) }

    @Suppress("UNCHECKED_CAST")
    fun load(file: File): CoverageShard {
        val options = LoaderOptions().apply {
            // An add-on ledger is our own content, but the parser is shared with code that reads
            // untrusted input elsewhere; keep the habit.
            isAllowDuplicateKeys = false
            codePointLimit = 16 * 1024 * 1024
        }
        val root = Yaml(options).load<Map<String, Any?>>(file.readText())
            ?: error("${file.name}: empty")

        val domain = root["domain"] as? String ?: error("${file.name}: missing `domain`")
        val spec = root["spec"] as? String ?: error("${file.name}: missing `spec`")
        val upstream = root["upstream"] as? Map<String, Any?>

        val entries = (root["entries"] as? List<Map<String, Any?>>).orEmpty().map { raw ->
            val id = raw["id"] as? String ?: error("${file.name}: entry with no `id`")
            val status = raw["status"] as? String ?: error("${file.name}: `$id` has no `status`")
            require(status in validStatuses) { "${file.name}: `$id` has unknown status `$status`" }
            CoverageEntry(
                id = id,
                status = status,
                impl = raw["impl"] as? String,
                fidelity = raw["fidelity"] as? String,
                conformance = (raw["conformance"] as? List<String>).orEmpty(),
                fields = (raw["fields"] as? Map<String, String>).orEmpty(),
                diagnostics = (raw["diagnostics"] as? List<String>).orEmpty(),
                notes = raw["notes"] as? String,
                shard = domain,
                spec = spec,
            )
        }

        return CoverageShard(
            file = file,
            domain = domain,
            spec = spec,
            upstreamSource = upstream?.get("source") as? String,
            upstreamPointer = upstream?.get("pointer") as? String,
            upstreamIdField = upstream?.get("idField") as? String,
            entries = entries,
        )
    }
}

/**
 * Accumulates problems so that one build reports all of them.
 *
 * The alternative — throwing on the first — turns a ten-minute fix into ten builds, and the
 * specification checks run on every push.
 */
class Problems(private val taskName: String) {
    private val messages = mutableListOf<String>()

    fun report(where: String, message: String) {
        messages += "  $where: $message"
    }

    fun failIfAny() {
        if (messages.isEmpty()) return
        throw org.gradle.api.GradleException(
            buildString {
                appendLine("$taskName found ${messages.size} problem(s):")
                messages.sorted().forEach { appendLine(it) }
                appendLine()
                appendLine("See spec/process.md section 5 for what each check enforces.")
            }
        )
    }

    val count: Int get() = messages.size
}
