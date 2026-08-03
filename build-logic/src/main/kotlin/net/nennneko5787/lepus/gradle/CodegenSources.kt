package net.nennneko5787.lepus.gradle

import java.io.File
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml

/**
 * The in-memory form of `spec/upstream/codegen.yaml`.
 *
 * Upstream files that exist to be GENERATED FROM rather than to be diffed against the ledger. The
 * lock file's `usedBy.kind` has always allowed `codegen`; what was missing was any way to say so,
 * because [UpdateUpstreamLockTask] derived its whole file set from coverage shards. A path could
 * therefore only be pinned by pretending it was a list of Bedrock feature identifiers, which
 * `spec/upstream/fetch.md` calls worse than leaving it out.
 */
data class CodegenTarget(
    val name: String,
    /** The fully qualified class this target emits, for the lock file to be readable. */
    val generates: String?,
    val sources: List<String>,
)

object CodegenSources {

    /** Empty when the file is absent, which is the honest state of a project generating nothing. */
    @Suppress("UNCHECKED_CAST")
    fun load(file: File): List<CodegenTarget> {
        if (!file.isFile) return emptyList()
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            codePointLimit = 4 * 1024 * 1024
        }
        val root = Yaml(options).load<Map<String, Any?>>(file.readText()) ?: return emptyList()
        return (root["targets"] as? List<Map<String, Any?>>).orEmpty().map { raw ->
            val name = raw["name"] as? String
                ?: error("${file.name}: a codegen target has no `name`")
            val sources = (raw["sources"] as? List<String>).orEmpty()
            // A target with no sources pins nothing and would look wired up. Louder than useful is
            // the right side to err on for a file nobody reads until it is wrong.
            require(sources.isNotEmpty()) { "${file.name}: codegen target `$name` lists no sources" }
            CodegenTarget(name, raw["generates"] as? String, sources)
        }
    }
}
