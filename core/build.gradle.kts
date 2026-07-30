// Conventions shared by every core module.
//
// Compiled with --release 21 regardless of which Minecraft node consumes it: Java 21 bytecode runs
// on the Java 25 runtime that 26.2 requires, and one artifact for every node is worth giving up
// Java 25 language features here. SC-220 section 8.

plugins {
    `java-library`
}

subprojects {
    apply(plugin = "java-library")

    group = "net.nennneko5787.sweetcookie"
    version = "0.1.0"

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(21)
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 21
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial"))
    }

    dependencies {
        val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
        "testImplementation"(platform(libs.findLibrary("junit-bom").get()))
        "testImplementation"(libs.findLibrary("junit-jupiter").get())
        "testRuntimeOnly"(libs.findLibrary("junit-launcher").get())
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("failed") }
    }
}
