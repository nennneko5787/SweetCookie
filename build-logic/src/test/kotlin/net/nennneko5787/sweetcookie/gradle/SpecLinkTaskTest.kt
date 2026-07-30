package net.nennneko5787.sweetcookie.gradle

import java.io.File
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

/**
 * Traceability: `@SpecImpl` and `@ProvesSpec` against the ledger, in both directions.
 *
 * Two of these are regression tests for bugs this task actually shipped with, both of which made it
 * **pass when it should have failed** — the worst possible failure mode for a correctness gate:
 *
 *  - it scanned zero classes, because a Gradle `FileTree` yields files and the code filtered for
 *    directories;
 *  - it keyed the ledger by feature id alone, silently dropping 7 of 833 entries where Bedrock
 *    overloads a component name across domains.
 */
class SpecLinkTaskTest {

    @TempDir
    lateinit var tmp: File

    private fun task(fixture: SpecFixture, classes: List<File>): SpecLinkTask {
        val t = fixture.project.tasks.register("l", SpecLinkTask::class.java).get()
        t.specDir.set(fixture.specDir)
        t.classDirs.from(classes)
        return t
    }

    private fun expectFailure(fixture: SpecFixture, classes: List<File>, containing: String) {
        val error = assertThrows<GradleException> { task(fixture, classes).check() }
        assertTrue(
            error.message!!.contains(containing),
            "expected a failure mentioning \"$containing\", got:\n${error.message}"
        )
    }

    // ── regression: the scan matched nothing ────────────────────────────────────

    @Test
    fun `annotations are found when classDirs contains individual class files`() {
        // A Gradle FileTree yields FILES, not directories. The original implementation filtered for
        // directories, scanned nothing, and reported "all links resolve" on every input — including
        // a ledger full of lies.
        //
        // Proving the scan is LIVE, rather than merely not-crashing, means feeding it a class that
        // claims a feature the ledger does not have. A dead scan passes this; a live one fails.
        val fixture = SpecFixture(tmp)
            .normative("SC-110")
            .shardOf("format", "SC-110", SpecFixture.Entry("pack/manifest", "stub"))
        val classDir = fixture.classesWith(
            listOf(AnnotationSpec("a.b.Thing", "SpecImpl", listOf("SC-110#pack/not_in_the_ledger")))
        )
        val individualFiles = classDir.walkTopDown().filter { it.extension == "class" }.toList()
        assertTrue(individualFiles.isNotEmpty(), "fixture produced no class files")

        expectFailure(fixture, individualFiles, "pack/not_in_the_ledger")
    }

    @Test
    fun `annotations are found when classDirs contains a directory`() {
        val fixture = SpecFixture(tmp)
            .normative("SC-110")
            .shardOf("format", "SC-110", SpecFixture.Entry("pack/manifest", "stub"))
        val classDir = fixture.classesWith(
            listOf(AnnotationSpec("a.b.Thing", "SpecImpl", listOf("SC-110#pack/manifest")))
        )
        assertDoesNotThrow { task(fixture, listOf(classDir)).check() }
    }

    // ── regression: entries dropped by an id-only key ───────────────────────────

    @Test
    fun `overloaded feature names are both checked, not silently deduplicated`() {
        // minecraft:collision_box exists under SC-150 (block) and SC-160 (entity). Keying the
        // ledger by id alone kept one and dropped the other, leaving it unverified. Here the
        // SC-160 copy is the dishonest one; if it is dropped, this test passes when it must fail.
        val fixture = SpecFixture(tmp)
            .normative("SC-150", "SC-160")
            .shardOf(
                "block-components", "SC-150",
                SpecFixture.Entry("minecraft:collision_box", "stub"),
            )
            .shardOf(
                "entity-components", "SC-160",
                SpecFixture.Entry(
                    "minecraft:collision_box", "partial",
                    impl = "net.nennneko5787.sweetcookie.runtime.NeverCompiled",
                    fidelity = "Bedrock allows a single AABB up to 1.875 blocks; we clamp to one " +
                        "block, so oversized hitboxes are visibly smaller than on Bedrock.",
                    conformance = listOf("entity/collision_box"),
                ),
            )
            .conformanceCase(
                "entity/collision_box",
                """
                id: entity/collision_box
                tier: T0
                spec: SC-160
                features:
                  - minecraft:collision_box
                description: Placeholder so the conformance link is not what fails this test.
                pack:
                  extends: minimal_bp
                """,
            )
        // One real annotation, so the scan counts as live and direction 3 is not skipped. Without
        // it the fresh-clone tolerance would let the dishonest SC-160 entry through.
        val classDir = fixture.classesWith(
            listOf(AnnotationSpec("a.b.Blocks", "SpecImpl", listOf("SC-150#minecraft:collision_box")))
        )
        expectFailure(fixture, listOf(classDir), "NeverCompiled")
    }

    @Test
    fun `an annotation naming a feature under the wrong spec is rejected`() {
        // SC-150#collision_box exists; SC-140#collision_box does not. Resolving by id alone would
        // accept this.
        val fixture = SpecFixture(tmp)
            .normative("SC-140", "SC-150")
            .shardOf("block-components", "SC-150", SpecFixture.Entry("minecraft:collision_box", "stub"))
            .shardOf("filters", "SC-140", SpecFixture.Entry("is_family", "stub"))
        val classDir = fixture.classesWith(
            listOf(AnnotationSpec("a.b.Wrong", "SpecImpl", listOf("SC-140#minecraft:collision_box")))
        )
        expectFailure(fixture, listOf(classDir), "has no coverage entry")
    }

    // ── the ordinary rules ──────────────────────────────────────────────────────

    @Test
    fun `an annotation naming a nonexistent specification is rejected`() {
        val fixture = SpecFixture(tmp)
            .normative("SC-110")
            .shardOf("format", "SC-110", SpecFixture.Entry("pack/manifest", "stub"))
        val classDir = fixture.classesWith(
            listOf(AnnotationSpec("a.b.Thing", "SpecImpl", listOf("SC-999")))
        )
        expectFailure(fixture, listOf(classDir), "not a document in spec/normative/")
    }

    @Test
    fun `an implementation nothing tracks is rejected`() {
        // How a feature quietly ends up shipped-but-undocumented.
        val fixture = SpecFixture(tmp)
            .normative("SC-110")
            .shardOf("format", "SC-110", SpecFixture.Entry("pack/manifest", "stub"))
        val classDir = fixture.classesWith(
            listOf(AnnotationSpec("a.b.Orphan", "SpecImpl", listOf("SC-110#pack/subpacks")))
        )
        expectFailure(fixture, listOf(classDir), "is in no coverage shard")
    }

    @Test
    fun `a claimed conformance case that does not exist is rejected`() {
        val fixture = SpecFixture(tmp)
            .normative("SC-110")
            .shardOf(
                "format", "SC-110",
                SpecFixture.Entry(
                    "pack/manifest", "implemented",
                    impl = "a.b.Thing",
                    conformance = listOf("packaging/nope"),
                ),
            )
        val classDir = fixture.classesWith(
            listOf(AnnotationSpec("a.b.Thing", "SpecImpl", listOf("SC-110#pack/manifest")))
        )
        expectFailure(fixture, listOf(classDir), "which does not exist")
    }

    @Test
    fun `an empty scan does not fail a fresh clone`() {
        // A checkout that has not compiled anything should not be told its ledger is broken. CI
        // compiles first, which is where the check has teeth.
        val fixture = SpecFixture(tmp)
            .normative("SC-110")
            .shardOf(
                "format", "SC-110",
                SpecFixture.Entry(
                    "pack/manifest", "partial",
                    impl = "not.compiled.Yet",
                    fidelity = "Subpack selection is configured rather than inferred, so a pack " +
                        "shipping HD textures shows its base textures unless the tier is raised.",
                    conformance = listOf("packaging/manifest"),
                ),
            )
            .conformanceCase(
                "packaging/manifest",
                """
                id: packaging/manifest
                tier: T0
                spec: SC-110
                features:
                  - pack/manifest
                description: Placeholder case so the conformance link resolves in this test.
                pack:
                  extends: minimal_bp
                """,
            )
        assertDoesNotThrow { task(fixture, emptyList()).check() }
    }
}
