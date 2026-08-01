import net.nennneko5787.sweetcookie.gradle.AdrIndexTask
import net.nennneko5787.sweetcookie.gradle.FetchUpstreamTask
import net.nennneko5787.sweetcookie.gradle.SpecConformanceTask
import net.nennneko5787.sweetcookie.gradle.SpecLanguageTask
import net.nennneko5787.sweetcookie.gradle.SpecLinkTask
import net.nennneko5787.sweetcookie.gradle.SpecReportTask
import net.nennneko5787.sweetcookie.gradle.SpecUpstreamDiffTask
import net.nennneko5787.sweetcookie.gradle.SpecValidateTask
import net.nennneko5787.sweetcookie.gradle.UpdateUpstreamLockTask

// The specification checks. Constitution rule 9: the ledger is verified, not trusted.
//
// `./gradlew specAll` runs all of them and is what CI gates on. A green specAll means the ledger is
// not lying; it says nothing about whether the mod is any good.

val specDirectory = layout.projectDirectory.dir("spec")
val docsCompatibility = layout.projectDirectory.dir("docs/compatibility")

val specValidate = tasks.register<SpecValidateTask>("specValidate") {
    group = "specification"
    description = "Validate the coverage ledger and conformance cases against their schemas."
    specDir.set(specDirectory)
}

val specLanguage = tasks.register<SpecLanguageTask>("specLanguage") {
    group = "specification"
    description = "Reject CJK in spec/** outside the exempt paths (constitution rule 11)."
    specDir.set(specDirectory)
}

val adrIndex = tasks.register<AdrIndexTask>("adrIndex") {
    group = "specification"
    description = "Check that every ADR is well formed and its cross-links resolve."
    specDir.set(specDirectory)
}

val specLinks = tasks.register<SpecLinkTask>("specLinks") {
    group = "specification"
    description = "Verify @SpecImpl/@ProvesSpec against the ledger, in both directions."
    specDir.set(specDirectory)
    // Every compiled class the build produces, wherever it lands.
    classDirs.from(
        layout.projectDirectory.dir("core").asFileTree.matching { include("**/build/classes/**") },
        layout.projectDirectory.dir("versions").asFileTree.matching { include("**/build/classes/**") },
    )
    // COMPILE FIRST, explicitly. This used to say "scanning nothing is tolerated, CI compiles
    // first" - which made the check pass by finding no annotations whenever it happened to run
    // before the compile, exactly the shape of failure this project keeps finding and refusing.
    // Gradle caught it as an undeclared dependency the first time specAll and chiseledBuild ran in
    // one invocation; without the dependency the ORDER decided the result.
    // chiseledBuild, not chiseledCompile: the scanned tree is the whole versions/ directory, so
    // every task that writes anywhere under it - test classes, generated resources - is a producer
    // of this task input, and Gradle refuses to guess the order for any of them.
    dependsOn("chiseledBuild")
    listOf("api", "format", "molang", "registry", "script", "ui", "testkit").forEach { module ->
        dependsOn(gradle.includedBuild("core").task(":$module:classes"))
    }
    mustRunAfter(specValidate)
}

val upstreamCache = layout.projectDirectory.dir(".upstream-cache")

// Nothing from Mojang/bedrock-samples is committed - it is NOASSERTION-licensed, fetched at build
// time, and used only to generate code. Constitution rule 10, spec/upstream/fetch.md.
val fetchUpstreamMetadata = tasks.register<FetchUpstreamTask>("fetchUpstreamMetadata") {
    group = "specification"
    description = "Download the pinned Mojang metadata into .upstream-cache, verifying every hash."
    specDir.set(specDirectory)
    cacheDir.set(upstreamCache)
}

// Deliberately manual. Bumping the snapshot is how new Bedrock features enter view, and a human
// must see the resulting list of work rather than have the ledger silently re-baselined.
tasks.register<UpdateUpstreamLockTask>("updateUpstreamLock") {
    group = "specification"
    description = "Pin a new bedrock-samples snapshot. Pass --ref=<branch|tag|sha>."
    specDir.set(specDirectory)
}

val specUpstreamDiff = tasks.register<SpecUpstreamDiffTask>("specUpstreamDiff") {
    group = "specification"
    description = "Fail when Mojang publishes a feature identifier the ledger does not know about."
    specDir.set(specDirectory)
    // Not a dependency: specAll must work offline on a fresh clone, where the task skips with an
    // explanation instead of failing. CI fetches first.
    mustRunAfter(fetchUpstreamMetadata)
}

// Runs the corpus and then checks the ledger against what actually ran. The dependency on the
// included build is the point: a check that reads a stale results file, or none, would report
// success without evidence.
val specConformance = tasks.register<SpecConformanceTask>("specConformance") {
    group = "specification"
    description = "Run the conformance corpus and verify every ledger reference to it."
    specDir.set(specDirectory)
    resultsFile.set(
        layout.projectDirectory.file("core/testkit/build/conformance-results.json")
    )
    dependsOn(gradle.includedBuild("core").task(":testkit:test"))
    mustRunAfter(specValidate)
}

val specReport = tasks.register<SpecReportTask>("specReport") {
    group = "specification"
    description = "Check docs/compatibility/** against the ledger. Pass --write to regenerate."
    specDir.set(specDirectory)
    outputDir.set(docsCompatibility)
    mustRunAfter(specValidate)
}

tasks.register("specAll") {
    group = "specification"
    description = "Every specification check. What CI gates on."
    dependsOn(
        specValidate, specLanguage, adrIndex, specLinks, specConformance, specUpstreamDiff,
        specReport,
    )
}
