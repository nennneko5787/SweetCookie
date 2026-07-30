package net.nennneko5787.sweetcookie.gradle

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration

/**
 * Fetching Mojang's machine-readable add-on metadata.
 *
 * Nothing from `Mojang/bedrock-samples` is committed. It is licensed NOASSERTION, so it is fetched
 * at build time, pinned by commit SHA and per-file SHA-256, and used only to generate code
 * (constitution rule 10, `spec/upstream/fetch.md`).
 *
 * Individual files are fetched over HTTPS rather than cloning: the repository is large, and the
 * lock file already names exactly which files matter.
 */
object Upstream {

    const val REPOSITORY = "https://github.com/Mojang/bedrock-samples"
    private const val OWNER_REPO = "Mojang/bedrock-samples"

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /** Resolves a branch, tag or SHA to a full commit SHA. */
    fun resolveCommit(ref: String): String {
        if (ref.matches(Regex("^[0-9a-f]{40}$"))) return ref
        val body = getString("https://api.github.com/repos/$OWNER_REPO/commits/$ref")
        val sha = Regex("\"sha\"\\s*:\\s*\"([0-9a-f]{40})\"").find(body)?.groupValues?.get(1)
        return sha ?: error("could not resolve `$ref` to a commit in $OWNER_REPO")
    }

    /** Raw bytes of one file at one commit, or null when the path does not exist there. */
    fun fetchFile(commit: String, path: String): ByteArray? {
        val encoded = path.split('/').joinToString("/") {
            URLEncoder.encode(it, Charsets.UTF_8).replace("+", "%20")
        }
        val request = HttpRequest.newBuilder(URI.create("https://raw.githubusercontent.com/$OWNER_REPO/$commit/$encoded"))
            .header("User-Agent", "sweetcookie-build")
            .timeout(Duration.ofSeconds(120))
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
        return when (response.statusCode()) {
            200 -> response.body()
            404 -> null
            else -> error("HTTP ${response.statusCode()} fetching $path at $commit")
        }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun getString(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "sweetcookie-build")
            .header("Accept", "application/vnd.github+json")
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "HTTP ${response.statusCode()} for $url" }
        return response.body()
    }
}

/** One entry in `spec/upstream/bedrock-samples.lock.json`. */
data class LockedFile(
    val path: String,
    val sha256: String,
    val bytes: Long,
    /** `coverage` shard names, `codegen` targets or `corpus` labels that consume this file. */
    val usedBy: List<Pair<String, String>>,
)
