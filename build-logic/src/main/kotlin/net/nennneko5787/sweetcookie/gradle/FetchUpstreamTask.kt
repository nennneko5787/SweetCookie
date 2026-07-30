package net.nennneko5787.sweetcookie.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Downloads the pinned Mojang metadata into `.upstream-cache/`, verifying every file's hash.
 *
 * `.upstream-cache/` is gitignored and nothing from it ever reaches a released artifact
 * (constitution rule 10). A file whose hash does not match the lock is a hard failure, not a
 * warning: the point of pinning is that the build cannot silently generate from different data than
 * it was reviewed against.
 *
 * Already-correct files are left alone, so a second run costs nothing.
 */
abstract class FetchUpstreamTask : DefaultTask() {

    @get:InputDirectory
    abstract val specDir: DirectoryProperty

    @get:OutputDirectory
    abstract val cacheDir: DirectoryProperty

    private val json = ObjectMapper()

    @TaskAction
    fun fetch() {
        val lockFile = specDir.get().asFile.resolve("upstream/bedrock-samples.lock.json")
        if (!lockFile.isFile) {
            throw org.gradle.api.GradleException(
                "No lock file. Run `./gradlew updateUpstreamLock` first - see spec/upstream/fetch.md."
            )
        }

        val lock = json.readTree(lockFile)
        val commit = lock["commit"]?.asText().orEmpty()
        if (commit.isBlank() || commit.all { it == '0' }) {
            throw org.gradle.api.GradleException(
                "The upstream snapshot is not pinned yet (commit is all zeroes).\n" +
                    "Run `./gradlew updateUpstreamLock` to pin one. Pinning is deliberate, so this\n" +
                    "task will not choose a commit for you - see spec/upstream/fetch.md."
            )
        }

        val cache = cacheDir.get().asFile
        cache.mkdirs()
        val files = lock["files"] ?: return

        var fetched = 0
        var reused = 0
        val problems = Problems("fetchUpstreamMetadata")

        for (entry in files) {
            val path = entry["path"].asText()
            val expected = entry["sha256"].asText()
            val target = File(cache, path)

            if (target.isFile && Upstream.sha256(target.readBytes()) == expected) {
                reused++
                continue
            }

            val bytes = Upstream.fetchFile(commit, path)
            if (bytes == null) {
                problems.report(path, "not found at pinned commit $commit")
                continue
            }
            val actual = Upstream.sha256(bytes)
            if (actual != expected) {
                // Either the lock is stale or someone is serving different content. Both mean the
                // build must stop rather than generate from unreviewed data.
                problems.report(
                    path,
                    "hash mismatch at $commit\n      expected $expected\n      actual   $actual"
                )
                continue
            }
            target.parentFile.mkdirs()
            target.writeBytes(bytes)
            fetched++
        }

        problems.failIfAny()
        logger.lifecycle(
            "fetchUpstreamMetadata: $fetched fetched, $reused already current, at $commit."
        )
    }
}
