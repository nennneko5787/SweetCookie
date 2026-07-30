package net.nennneko5787.sweetcookie.gradle

import java.io.File
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

/**
 * Constitution rule 11: the specification is English, with named exceptions.
 *
 * The exemptions matter as much as the rule. A lint that rejects the Japanese translations it was
 * told to allow, or that trips on a Bedrock pack fragment quoted in a code fence, gets disabled —
 * and a disabled lint enforces nothing.
 */
class SpecLanguageTaskTest {

    @TempDir
    lateinit var tmp: File

    private fun run(fixture: SpecFixture) {
        val task = fixture.project.tasks.register("lang", SpecLanguageTask::class.java).get()
        task.specDir.set(fixture.specDir)
        task.check()
    }

    @Test
    fun `English passes`() {
        val fixture = SpecFixture(tmp)
            .file("normative/SC-100-packaging.md", "# SC-100\n\nPacks are ZIP archives.\n")
        assertDoesNotThrow { run(fixture) }
    }

    @Test
    fun `Japanese in a normative document is rejected`() {
        val fixture = SpecFixture(tmp)
            .file("normative/SC-100-packaging.md", "# SC-100\n\nパックは ZIP です。\n")
        val error = assertThrows<GradleException> { run(fixture) }
        assertTrue(error.message!!.contains("CJK character"), error.message)
    }

    @Test
    fun `the line number points at the offending line`() {
        val fixture = SpecFixture(tmp)
            .file("normative/SC-100-packaging.md", "# SC-100\n\nfine\nfine\nパック\n")
        val error = assertThrows<GradleException> { run(fixture) }
        assertTrue(
            error.message!!.contains("SC-100-packaging.md:5"),
            "expected the report to name line 5, got:\n${error.message}"
        )
    }

    @Test
    fun `translations under normative-ja are exempt`() {
        val fixture = SpecFixture(tmp)
            .file("normative/ja/SC-100-packaging.md", "# SC-100\n\nパックは ZIP です。\n")
        assertDoesNotThrow { run(fixture) }
    }

    @Test
    fun `transient feature notes are exempt`() {
        // This is where the friction is deliberately removed: work notes are archived when the
        // work lands, and forcing English there buys nothing.
        val fixture = SpecFixture(tmp)
            .file("features/0001-molang/spec.md", "# Molang\n\n式を評価する。\n")
        assertDoesNotThrow { run(fixture) }
    }

    @Test
    fun `a code fence may contain Japanese`() {
        // A Bedrock pack legitimately contains Japanese strings, and a spec has to be able to quote
        // one as an example.
        val fixture = SpecFixture(tmp).file(
            "normative/SC-100-packaging.md",
            """
            # SC-100

            A `.lang` file may contain any language:

            ```
            item.sweetcookie.wand=魔法の杖
            ```

            and that is not a violation.
            """.trimIndent(),
        )
        assertDoesNotThrow { run(fixture) }
    }

    @Test
    fun `Japanese after a closed code fence is still rejected`() {
        // The fence tracker must toggle, not latch. A latching implementation would exempt the
        // whole rest of the file after the first fence — silently disabling the lint.
        val fixture = SpecFixture(tmp).file(
            "normative/SC-100-packaging.md",
            """
            # SC-100

            ```
            item.sweetcookie.wand=魔法の杖
            ```

            パックは ZIP です。
            """.trimIndent(),
        )
        assertThrows<GradleException> { run(fixture) }
    }
}
