package net.nennneko5787.lepus.gradle

import java.io.File
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

/**
 * Builds a throwaway `spec` tree on disk so the checks can be run against inputs we control.
 *
 * The JSON Schemas are **copied from the real repository**, not stubbed. A test that validates
 * against a hand-written toy schema proves the test author understood the rules, which is not the
 * question — the question is whether the shipped schema catches a shipped mistake.
 */
class SpecFixture(val root: File) {

    val specDir: File = root.resolve("spec").apply { mkdirs() }

    /** A Gradle project rooted at the fixture, for instantiating tasks under test. */
    val project: Project by lazy { ProjectBuilder.builder().withProjectDir(root).build() }

    init {
        val realSchemas = locateRealSchemas()
        specDir.resolve("schemas").mkdirs()
        for (name in listOf("coverage.schema.json", "conformance-case.schema.json")) {
            realSchemas.resolve(name).copyTo(specDir.resolve("schemas/$name"), overwrite = true)
        }
        listOf("coverage", "conformance", "normative", "adr").forEach {
            specDir.resolve(it).mkdirs()
        }
    }

    /** Declares a normative document so that references to it resolve. */
    fun normative(vararg ids: String): SpecFixture = apply {
        ids.forEach { specDir.resolve("normative/$it-test.md").writeText("# $it - Test\n") }
    }

    /** Writes a coverage shard verbatim. Tests pass deliberately broken YAML when that is the point. */
    fun shard(domain: String, yaml: String): SpecFixture = apply {
        specDir.resolve("coverage/$domain.yaml").writeText(yaml.trimIndent() + "\n")
    }

    /**
     * A shard of one entry, defaulting everything the caller is not testing.
     *
     * `entries` is deliberately a list so a test can put two entries with the same id in one shard.
     */
    fun shardOf(domain: String, spec: String, vararg entries: Entry): SpecFixture = shard(
        domain,
        buildString {
            appendLine("domain: $domain")
            appendLine("spec: $spec")
            appendLine("upstream: null")
            appendLine("entries:")
            entries.forEach { e ->
                appendLine("  - id: \"${e.id}\"")
                appendLine("    status: ${e.status}")
                e.impl?.let { appendLine("    impl: $it") }
                e.fidelity?.let { appendLine("    fidelity: \"$it\"") }
                if (e.conformance.isNotEmpty()) {
                    appendLine("    conformance:")
                    e.conformance.forEach { appendLine("      - $it") }
                }
                if (e.fields.isNotEmpty()) {
                    appendLine("    fields:")
                    e.fields.forEach { (k, v) -> appendLine("      $k: $v") }
                }
            }
        }
    )

    data class Entry(
        val id: String,
        val status: String,
        val impl: String? = null,
        val fidelity: String? = null,
        val conformance: List<String> = emptyList(),
        val fields: Map<String, String> = emptyMap(),
    )

    fun conformanceCase(id: String, yaml: String): SpecFixture = apply {
        specDir.resolve("conformance/$id").mkdirs()
        specDir.resolve("conformance/$id/case.yaml").writeText(yaml.trimIndent() + "\n")
    }

    fun file(relative: String, content: String): SpecFixture = apply {
        val f = specDir.resolve(relative)
        f.parentFile.mkdirs()
        f.writeText(content)
    }

    /**
     * Compiles a class carrying the given annotation, so the bytecode scan has something real to
     * read. Returns the directory to hand to `classDirs`.
     *
     * Writing the class file by hand with ASM rather than invoking javac keeps the test fast and
     * removes a dependency on a compiler being present.
     */
    fun classesWith(annotations: List<AnnotationSpec>): File {
        val dir = root.resolve("classes").apply { mkdirs() }
        annotations.forEach { spec ->
            val bytes = AnnotatedClassWriter.write(spec)
            val file = dir.resolve(spec.className.replace('.', '/') + ".class")
            file.parentFile.mkdirs()
            file.writeBytes(bytes)
        }
        return dir
    }

    private fun locateRealSchemas(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val candidate = dir.resolve("spec/schemas")
            if (candidate.resolve("coverage.schema.json").isFile) return candidate
            dir = dir.parentFile
        }
        error("could not locate spec/schemas from ${System.getProperty("user.dir")}")
    }
}

data class AnnotationSpec(
    val className: String,
    /** `SpecImpl` or `ProvesSpec`. */
    val annotation: String,
    val values: List<String>,
)
