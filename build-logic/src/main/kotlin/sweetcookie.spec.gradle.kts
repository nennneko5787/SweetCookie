import net.nennneko5787.sweetcookie.gradle.AdrIndexTask
import net.nennneko5787.sweetcookie.gradle.SpecLanguageTask
import net.nennneko5787.sweetcookie.gradle.SpecLinkTask
import net.nennneko5787.sweetcookie.gradle.SpecReportTask
import net.nennneko5787.sweetcookie.gradle.SpecUpstreamDiffTask
import net.nennneko5787.sweetcookie.gradle.SpecValidateTask

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
    // Every compiled class the build produces, wherever it lands. Scanning nothing is tolerated
    // so that a fresh clone is not told its ledger is broken; CI compiles first.
    classDirs.from(
        layout.projectDirectory.dir("core").asFileTree.matching { include("**/build/classes/**") },
        layout.projectDirectory.dir("versions").asFileTree.matching { include("**/build/classes/**") },
    )
    mustRunAfter(specValidate)
}

val specUpstreamDiff = tasks.register<SpecUpstreamDiffTask>("specUpstreamDiff") {
    group = "specification"
    description = "Fail when Mojang publishes a feature identifier the ledger does not know about."
    specDir.set(specDirectory)
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
    dependsOn(specValidate, specLanguage, adrIndex, specLinks, specUpstreamDiff, specReport)
}
