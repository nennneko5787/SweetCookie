package net.nennneko5787.lepus.gradle

import java.time.Instant
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * Pins a new `Mojang/bedrock-samples` snapshot.
 *
 * **Deliberately manual, never automatic** (`spec/upstream/fetch.md`). Bumping the snapshot is how
 * new Bedrock features enter the project's field of view, and the whole point is that a human sees
 * the resulting list of work rather than having the ledger silently re-baselined.
 *
 * The file set is derived from the ledger: every distinct `upstream.source` declared by a coverage
 * shard. Nothing is hard-coded here, so adding a shard with an upstream source is enough to make
 * this task start tracking it.
 *
 * After running this, `specUpstreamDiff` is expected to FAIL, listing what Bedrock added. Resolving
 * that by adding `status: stub` entries is the point of the exercise, not a chore around it.
 */
abstract class UpdateUpstreamLockTask : DefaultTask() {

    @get:InputDirectory
    abstract val specDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val ref: Property<String>

    // Not named setRef: that collides with the setter Gradle generates for the abstract property,
    // and the decorated class then fails to build.
    @Option(option = "ref", description = "Branch, tag or commit to pin. Defaults to main.")
    fun refOption(value: String) = ref.set(value)

    @TaskAction
    fun update() {
        val spec = specDir.get().asFile
        val requested = ref.orNull ?: "main"

        logger.lifecycle("updateUpstreamLock: resolving `$requested` in ${Upstream.REPOSITORY} ...")
        val commit = Upstream.resolveCommit(requested)
        logger.lifecycle("updateUpstreamLock: commit $commit")

        // Every distinct upstream source the ledger declares, across every selector, plus the ones
        // declared for code generation. Sorted so the lock file is stable and its diffs readable.
        //
        // TWO KINDS, ONE LOCK. A coverage source is a list of Bedrock's feature identifiers and is
        // diffed against the ledger; a codegen source is data a generator reads. Keeping the kinds
        // apart in `usedBy` is what lets `specUpstreamDiff` ignore the second sort, which it must:
        // an English language file is not a feature list, and reading it as one manufactures ledger
        // entries out of prose.
        val sources = sortedMapOf<String, MutableList<Pair<String, String>>>()
        CoverageLoader.loadAll(spec.resolve("coverage")).forEach { shard ->
            shard.upstream.forEach { selector ->
                sources.getOrPut(selector.source) { mutableListOf() } += "coverage" to shard.domain
            }
        }
        CodegenSources.load(spec.resolve("upstream/codegen.yaml")).forEach { target ->
            target.sources.forEach { path ->
                sources.getOrPut(path) { mutableListOf() } += "codegen" to target.name
            }
        }

        if (sources.isEmpty()) {
            throw org.gradle.api.GradleException(
                "Nothing declares an upstream source - neither a coverage shard's `upstream` nor a\n" +
                    "target in spec/upstream/codegen.yaml - so there is nothing to pin."
            )
        }

        val locked = mutableListOf<LockedFile>()
        val missing = mutableListOf<String>()

        for ((path, users) in sources) {
            val bytes = Upstream.fetchFile(commit, path)
            if (bytes == null) {
                missing += "$path (declared by ${users.joinToString(", ") { it.second }})"
                continue
            }
            locked += LockedFile(
                path = path,
                sha256 = Upstream.sha256(bytes),
                bytes = bytes.size.toLong(),
                usedBy = users.distinct().sortedWith(compareBy({ it.first }, { it.second })),
            )
            logger.lifecycle("  %-72s %,10d B".format(path, bytes.size))
        }

        // A declared source that does not exist upstream is a defect in the ledger, not a warning.
        // It means a shard believes it is being checked against Mojang and silently is not.
        if (missing.isNotEmpty()) {
            throw org.gradle.api.GradleException(
                buildString {
                    appendLine("updateUpstreamLock: ${missing.size} declared upstream source(s) do not exist at $commit:")
                    missing.sorted().forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("Fix the `upstream.source` in those shards, or set it to null if the")
                    appendLine("domain genuinely has no machine-readable upstream list.")
                }
            )
        }

        val engineVersion = readEngineVersion(commit)
        val lockFile = spec.resolve("upstream/bedrock-samples.lock.json")
        lockFile.parentFile.mkdirs()
        lockFile.writeText(render(commit, requested, engineVersion, locked))

        logger.lifecycle(
            "updateUpstreamLock: pinned ${locked.size} file(s) at $commit " +
                "(engine ${engineVersion ?: "unknown"})."
        )
        logger.lifecycle("Next: ./gradlew fetchUpstreamMetadata specUpstreamDiff  - expect it to fail with new work.")
    }

    /** `version.json` carries the creator-facing engine version, e.g. 1.26.30. */
    private fun readEngineVersion(commit: String): String? {
        val bytes = Upstream.fetchFile(commit, "version.json") ?: return null
        return Regex("\"latest\"\\s*:\\s*\\{[^}]*?\"version\"\\s*:\\s*\"([^\"]+)\"")
            .find(String(bytes, Charsets.UTF_8))?.groupValues?.get(1)
            ?: Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
                .find(String(bytes, Charsets.UTF_8))?.groupValues?.get(1)
    }

    private fun render(commit: String, ref: String, engine: String?, files: List<LockedFile>): String =
        buildString {
            appendLine("{")
            appendLine("  \"\$comment\": \"GENERATED by ./gradlew updateUpstreamLock. Pinning a new snapshot is a deliberate act - see spec/upstream/fetch.md. NO UPSTREAM CONTENT IS COMMITTED; these hashes make the fetch reproducible and tamper-evident.\",")
            appendLine()
            appendLine("  \"repository\": \"${Upstream.REPOSITORY}\",")
            appendLine("  \"license\": \"NOASSERTION\",")
            appendLine("  \"ref\": \"$ref\",")
            appendLine("  \"commit\": \"$commit\",")
            appendLine("  \"fetchedAt\": \"${Instant.now().toString().substringBefore('.')}Z\",")
            appendLine()
            appendLine("  \"targetEngineVersion\": \"${engine ?: "unknown"}\",")
            appendLine()
            appendLine("  \"files\": [")
            files.forEachIndexed { index, f ->
                appendLine("    {")
                appendLine("      \"path\": \"${f.path.replace("\"", "\\\"")}\",")
                appendLine("      \"sha256\": \"${f.sha256}\",")
                appendLine("      \"bytes\": ${f.bytes},")
                appendLine("      \"usedBy\": [")
                f.usedBy.forEachIndexed { j, (kind, name) ->
                    appendLine("        { \"kind\": \"$kind\", \"name\": \"$name\" }${if (j == f.usedBy.lastIndex) "" else ","}")
                }
                appendLine("      ]")
                appendLine("    }${if (index == files.lastIndex) "" else ","}")
            }
            appendLine("  ]")
            appendLine("}")
        }
}
