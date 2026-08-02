package net.nennneko5787.lepus.gradle

import java.io.File
import java.util.Locale
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

/**
 * The ledger is checked, not trusted (constitution rule 9). These tests check the checker.
 *
 * Each one states a lie the ledger must not be able to tell, and asserts the build refuses it.
 */
class SpecValidateTaskTest {

    @TempDir
    lateinit var tmp: File

    private fun run(fixture: SpecFixture) {
        val task = fixture.project.tasks.register("v", SpecValidateTask::class.java).get()
        task.specDir.set(fixture.specDir)
        task.validate()
    }

    private fun expectFailure(fixture: SpecFixture, containing: String) {
        val error = assertThrows<GradleException> { run(fixture) }
        assertTrue(
            error.message!!.contains(containing),
            "expected a failure mentioning \"$containing\", got:\n${error.message}"
        )
    }

    @Test
    fun `a well-formed ledger passes`() {
        val fixture = SpecFixture(tmp)
            .normative("SC-140")
            .shardOf("filters", "SC-140", SpecFixture.Entry("is_family", "stub"))
        assertDoesNotThrow { run(fixture) }
    }

    @Test
    fun `implemented without a conformance case is rejected`() {
        // `implemented` means a test proves it. ADR-0011 made that a verification rather than a
        // promotion, which changed who writes the status and not what it has to be backed by.
        expectFailure(
            SpecFixture(tmp).normative("SC-140").shardOf(
                "filters", "SC-140",
                SpecFixture.Entry("is_family", "implemented", impl = "net.nennneko5787.lepus.x.C"),
            ),
            "no conformance case",
        )
    }

    @Test
    fun `implemented carrying a fidelity note is rejected`() {
        // A fidelity note states an observable difference from Bedrock. An entry claiming there is
        // none cannot have one, or the table and the prose beneath it say opposite things.
        expectFailure(
            SpecFixture(tmp).normative("SC-140").shardOf(
                "filters", "SC-140",
                SpecFixture.Entry(
                    "is_family", "implemented",
                    impl = "net.nennneko5787.lepus.x.C",
                    conformance = listOf("filter/is_family"),
                    fidelity = "Bedrock re-evaluates every tick and we re-evaluate every four.",
                ),
            ),
            "carries a `fidelity` note",
        )
    }

    @Test
    fun `implemented with a non-ok field is rejected`() {
        // A `fields` map holding `missing` is an enumerated divergence in table form. It says
        // `partial` however confidently the status line says otherwise - and it is the shape a
        // half-finished entry actually takes, because the author fills the table first.
        expectFailure(
            SpecFixture(tmp).normative("SC-140").shardOf(
                "filters", "SC-140",
                SpecFixture.Entry(
                    "is_family", "implemented",
                    impl = "net.nennneko5787.lepus.x.C",
                    conformance = listOf("filter/is_family"),
                    fields = mapOf("subject" to "ok", "operator" to "missing"),
                ),
            ),
            "non-`ok` field(s) operator",
        )
    }

    @Test
    fun `implemented with an all-ok fields map and no fidelity passes`() {
        // The positive half. Without it the two rules above would be satisfied by refusing every
        // `implemented` entry, which is a check that cannot pass rather than one that cannot fail.
        assertDoesNotThrow {
            run(
                SpecFixture(tmp).normative("SC-140").shardOf(
                    "filters", "SC-140",
                    SpecFixture.Entry(
                        "is_family", "implemented",
                        impl = "net.nennneko5787.lepus.x.C",
                        conformance = listOf("filter/is_family"),
                        fields = mapOf("subject" to "ok", "operator" to "ok"),
                    ),
                )
            )
        }
    }

    @Test
    fun `partial without an implementation class is rejected`() {
        expectFailure(
            SpecFixture(tmp).normative("SC-140").shardOf(
                "filters", "SC-140",
                SpecFixture.Entry(
                    "is_family", "partial",
                    fidelity = "Bedrock re-evaluates every tick; we re-evaluate every four, so a " +
                        "target that becomes invalid is dropped up to 150 ms late.",
                    conformance = listOf("filter/x"),
                ),
            ),
            "names no implementation class",
        )
    }

    @Test
    fun `a stub claiming an implementation is rejected`() {
        expectFailure(
            SpecFixture(tmp).normative("SC-140").shardOf(
                "filters", "SC-140",
                SpecFixture.Entry("is_family", "stub", impl = "net.nennneko5787.lepus.x.C"),
            ),
            "must not name an implementation class",
        )
    }

    @Test
    fun `a deliberate limitation with no fidelity note is rejected`() {
        expectFailure(
            SpecFixture(tmp).normative("SC-140").shardOf(
                "filters", "SC-140",
                SpecFixture.Entry("is_family", "wontfix"),
            ),
            "requires a `fidelity` note",
        )
    }

    @Test
    fun `a fidelity note that describes progress rather than behaviour is rejected`() {
        // The failure this rule exists to prevent: forty characters of "not done yet" passes a
        // length check and tells a user nothing about what their add-on will actually do.
        expectFailure(
            SpecFixture(tmp).normative("SC-140").shardOf(
                "filters", "SC-140",
                SpecFixture.Entry(
                    "is_family", "unsupported",
                    fidelity = "Not implemented yet, we will get to it in a future release soon.",
                ),
            ),
            "OBSERVABLE difference",
        )
    }

    @Test
    fun `Bedrock component names overloaded across domains are allowed`() {
        // minecraft:collision_box is BOTH an entity component and a block component, with different
        // semantics. The original design assumed feature ids were globally unique; they are not,
        // and the pair (spec, id) is the real key. See spec/ids.md.
        val fixture = SpecFixture(tmp)
            .normative("SC-150", "SC-160")
            .shardOf("block-components", "SC-150", SpecFixture.Entry("minecraft:collision_box", "stub"))
            .shardOf("entity-components", "SC-160", SpecFixture.Entry("minecraft:collision_box", "stub"))
        assertDoesNotThrow { run(fixture) }
    }

    @Test
    fun `the same id twice under the same spec is rejected`() {
        // Two shards claiming the same feature under one document means a status change in one is
        // invisible from the other.
        expectFailure(
            SpecFixture(tmp)
                .normative("SC-160")
                .shardOf("entity-components", "SC-160", SpecFixture.Entry("minecraft:loot", "stub"))
                .shardOf("entity-properties", "SC-160", SpecFixture.Entry("minecraft:loot", "stub")),
            "already declared in",
        )
    }

    @Test
    fun `a shard whose domain disagrees with its filename is rejected`() {
        // Otherwise a generated report attributes entries to the wrong page.
        expectFailure(
            SpecFixture(tmp).normative("SC-140").shard(
                "filters",
                """
                domain: something-else
                spec: SC-140
                upstream: null
                entries:
                  - id: is_family
                    status: stub
                """,
            ),
            "does not match the file name",
        )
    }

    @Test
    fun `an unknown status is rejected`() {
        expectFailure(
            SpecFixture(tmp).normative("SC-140").shard(
                "filters",
                """
                domain: filters
                spec: SC-140
                upstream: null
                entries:
                  - id: is_family
                    status: mostly_works
                """,
            ),
            "unknown status",
        )
    }

    @Test
    fun `a conformance case whose id disagrees with its path is rejected`() {
        expectFailure(
            SpecFixture(tmp)
                .normative("SC-140")
                .shardOf("filters", "SC-140", SpecFixture.Entry("is_family", "stub"))
                .conformanceCase(
                    "filter/is_family",
                    """
                    id: filter/somewhere_else
                    tier: T0
                    spec: SC-140
                    features:
                      - is_family
                    description: A case whose declared id does not match where it lives on disk.
                    pack:
                      extends: minimal_bp
                    """,
                ),
            "does not match its path",
        )
    }

    @Test
    fun `every problem is reported in one run, exactly once each`() {
        // Two properties at once:
        //
        //  - all three are reported, because reporting only the first turns a ten-minute fix into
        //    ten builds and these checks run on every push;
        //  - each is reported ONCE. The schema used to restate these rules, so every violation
        //    appeared twice - once readably and once as `allOf[2].then.not`. The conditional rules
        //    now live only in code (SC-000 section 2: schema governs shape, prose governs meaning).
        val fixture = SpecFixture(tmp).normative("SC-140").shardOf(
            "filters", "SC-140",
            SpecFixture.Entry("a", "implemented", impl = "net.nennneko5787.lepus.x.A"),
            SpecFixture.Entry("b", "stub", impl = "net.nennneko5787.lepus.x.B"),
            SpecFixture.Entry("c", "wontfix"),
        )
        val error = assertThrows<GradleException> { run(fixture) }
        assertTrue(
            error.message!!.contains("3 problem(s)"),
            "expected exactly three problems, got:\n${error.message}"
        )
    }

    @Test
    fun `schema messages are English regardless of the machine's locale`() {
        // The validator localises to the JVM default locale. On a Japanese machine that put
        // Japanese schema errors into a repository whose output policy is English - and into
        // whatever a user pastes into an issue or a search engine.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPAN)
            val fixture = SpecFixture(tmp).normative("SC-140").shard(
                "filters",
                """
                domain: filters
                spec: SC-140
                upstream: null
                entries:
                  - id: is_family
                    status: stub
                    diagnostics: "not-an-array"
                """,
            )
            val error = assertThrows<GradleException> { run(fixture) }
            assertTrue(
                error.message!!.none { it.code in 0x3000..0x9FFF },
                "schema messages leaked a non-English locale:\n${error.message}"
            )
        } finally {
            Locale.setDefault(original)
        }
    }
}
