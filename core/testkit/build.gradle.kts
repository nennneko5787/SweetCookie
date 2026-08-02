// Conformance harness: case loading, golden comparison, trace capture. spec/conformance/README.md.
//
// A main-source-set dependency on JUnit is deliberate — this module IS test infrastructure, and it
// is consumed by the test source sets of the other core modules and by the gametest harness in the
// Minecraft-dependent build.
//
// SnakeYAML is here and NOT in core/format. SC-110 §2.1 keeps the parser on its own JSON facade and
// nothing else; case.yaml is our own file format, read by our own harness, and the two must not
// start sharing a parser.

dependencies {
    api(project(":api"))
    api(project(":format"))
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter)
    implementation(libs.snakeyaml)
}

tasks.withType<Test>().configureEach {
    // The corpus lives outside this build. `core` is a separate build (ADR-0001), so it has no
    // rootProject pointing at the repository root and the path has to be handed in.
    systemProperty("lepus.specDir", rootProject.projectDir.parentFile.resolve("spec").path)
    systemProperty("lepus.conformanceResults",
        layout.buildDirectory.file("conformance-results.json").get().asFile.path)
    // Regenerate goldens: ./gradlew --project-dir core :testkit:test -Dlepus.accept=true
    System.getProperty("lepus.accept")?.let { systemProperty("lepus.accept", it) }
    outputs.upToDateWhen { false } // the corpus is an input this task cannot declare
}

// The survey: what real add-ons use and how much of it this build reads. spec/process.md §1.
//
// A task rather than a test, because add-ons are never committed here (constitution rule 10) and
// the path has to come from whoever is running it.
tasks.register<JavaExec>("survey") {
    group = "verification"
    description = "Report what installed add-ons use. -Paddons=<folder of packs>, repeatable by comma."
    mainClass.set("net.nennneko5787.lepus.core.testkit.AddonSurvey")
    classpath = sourceSets["main"].runtimeClasspath
    // Resolved at execution time so configuring the build without the property still works.
    argumentProviders.add(CommandLineArgumentProvider {
        (findProperty("addons") as String?)?.split(",")?.map(String::trim) ?: emptyList()
    })
}
