// Conformance harness: case loading, golden comparison, trace capture. spec/conformance/README.md.
//
// A main-source-set dependency on JUnit is deliberate — this module IS test infrastructure, and it
// is consumed by the test source sets of the other core modules and by the gametest harness in the
// Minecraft-dependent build.

dependencies {
    api(project(":api"))
    api(project(":format"))
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter)
}
