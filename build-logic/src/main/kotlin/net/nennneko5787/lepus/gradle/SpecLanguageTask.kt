package net.nennneko5787.lepus.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Enforces constitution rule 11: the specification is English.
 *
 * Exempt: `spec/normative/ja/` (translations), `spec/features/` (transient working notes, where the
 * friction is deliberately removed), and fenced code blocks (a Bedrock pack may legitimately contain
 * Japanese strings and a spec may need to quote one).
 *
 * The rule is not about preference. The domain vocabulary is already Mojang's English, feature
 * identifiers have to be greppable, and the audience for a Bedrock-compatibility mod is
 * international. Translations are informative; where they conflict, the English governs.
 */
abstract class SpecLanguageTask : DefaultTask() {

    @get:InputDirectory
    abstract val specDir: DirectoryProperty

    @TaskAction
    fun check() {
        val spec = specDir.get().asFile
        val problems = Problems("specLanguage")

        val exemptPrefixes = listOf("normative/ja/", "features/", "conformance/manual/")

        spec.walkTopDown()
            .filter { it.isFile && it.extension in setOf("md", "yaml", "yml", "json") }
            .forEach { file ->
                val rel = file.relativeTo(spec).invariantSeparatorsPath
                if (exemptPrefixes.any { rel.startsWith(it) }) return@forEach

                var inFence = false
                file.readLines().forEachIndexed { index, line ->
                    if (line.trimStart().startsWith("```")) {
                        inFence = !inFence
                        return@forEachIndexed
                    }
                    if (inFence) return@forEachIndexed
                    val hit = line.firstOrNull { isCjk(it) } ?: return@forEachIndexed
                    problems.report(
                        "$rel:${index + 1}",
                        "CJK character '$hit' outside an exempt path. " +
                            "Translations belong in spec/normative/ja/."
                    )
                }
            }

        problems.failIfAny()
        logger.lifecycle("specLanguage: clean.")
    }

    private fun isCjk(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c) ?: return false
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
            block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
    }
}
