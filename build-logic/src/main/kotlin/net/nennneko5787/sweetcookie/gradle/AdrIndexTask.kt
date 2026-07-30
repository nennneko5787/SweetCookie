package net.nennneko5787.sweetcookie.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Checks that every architecture decision record is well formed and that its cross-links resolve.
 *
 * ADRs are append-only history. An ADR that references a specification document or another ADR that
 * does not exist is a broken trail through exactly the decisions that were expensive enough to
 * write down.
 */
abstract class AdrIndexTask : DefaultTask() {

    @get:InputDirectory
    abstract val specDir: DirectoryProperty

    private val validStatuses = setOf("proposed", "accepted", "rejected")

    @TaskAction
    fun check() {
        val spec = specDir.get().asFile
        val adrDir = spec.resolve("adr")
        val problems = Problems("adrIndex")

        val specIds = spec.resolve("normative").listFiles().orEmpty()
            .filter { it.extension == "md" }
            .mapNotNull { Regex("^(SC-\\d{3})").find(it.name)?.groupValues?.get(1) }
            .toSet()

        val adrFiles = adrDir.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "md" && it.name != "index.md" }
            .sortedBy { it.name }

        val numbers = mutableMapOf<String, String>()

        for (file in adrFiles) {
            val where = "spec/adr/${file.name}"
            val number = Regex("^(\\d{4})-").find(file.name)?.groupValues?.get(1)
            if (number == null) {
                problems.report(where, "file name must start with a four-digit number")
                continue
            }
            numbers.put(number, file.name)?.let {
                problems.report(where, "ADR number $number is already used by $it")
            }
            if (number == "0000") continue // the template

            val text = file.readText()

            val status = Regex("\\*\\*Status:\\*\\*\\s*([a-z-]+)").find(text)?.groupValues?.get(1)
            when {
                status == null -> problems.report(where, "no `**Status:**` line")
                status !in validStatuses && !text.contains("superseded-by") ->
                    problems.report(where, "unknown status `$status`")
            }

            for (heading in listOf("## Context", "## Decision", "## Consequences", "## Reversal cost")) {
                // "## Decision" also matches "## Decision - the project licence", which is fine.
                if (!text.contains(Regex("^${Regex.escape(heading)}", RegexOption.MULTILINE))) {
                    problems.report(where, "missing section `$heading`")
                }
            }

            // Cross-links must resolve, in both directions.
            Regex("SC-(\\d{3})").findAll(text).map { "SC-" + it.groupValues[1] }.distinct()
                .forEach { referenced ->
                    if (referenced !in specIds) {
                        problems.report(where, "references `$referenced`, which does not exist")
                    }
                }
            Regex("ADR-(\\d{4})").findAll(text).map { it.groupValues[1] }.distinct()
                .forEach { referenced ->
                    if (adrFiles.none { it.name.startsWith("$referenced-") }) {
                        problems.report(where, "references ADR-$referenced, which does not exist")
                    }
                }
        }

        val index = adrDir.resolve("index.md")
        if (!index.isFile) {
            problems.report("spec/adr/index.md", "missing")
        } else {
            val indexText = index.readText()
            for (file in adrFiles) {
                if (file.name == "0000-template.md") continue
                if (!indexText.contains(file.name)) {
                    problems.report("spec/adr/index.md", "does not list ${file.name}")
                }
            }
        }

        problems.failIfAny()
        logger.lifecycle("adrIndex: ${adrFiles.size - 1} ADR(s), all links resolve.")
    }
}
