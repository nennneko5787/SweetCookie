plugins {
    id("java")
    id("net.neoforged.moddev")
}

val mc: String = stonecutter.current.version
val javaVersion = if (stonecutter.eval(mc, ">=26.1")) 25 else 21

// TODO(spike): move to stonecutter.properties.toml once the toml property API is confirmed.
val neoforgeVersion = mapOf(
    // Still -beta as of 2026-07-31; NeoForge has published no 26.2 release announcement.
    "26.2" to "26.2.0.40-beta",
    "1.21.11" to "21.11.45",
)[mc] ?: error("no neoforge version recorded for $mc")

group = "net.nennneko5787"
// One file per (Minecraft version x loader), which is what Modrinth and CurseForge expect.
version = "0.1.0+$mc-neoforge"
base.archivesName = "sweetcookie"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

sourceSets {
    named("main") {
        java.srcDir(rootProject.file("src/neoforge/java"))
        resources.srcDir(rootProject.file("src/neoforge/resources"))
    }
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
}

neoForge {
    version = neoforgeVersion

    mods {
        register("sweetcookie") {
            sourceSet(sourceSets["main"])
        }
    }
}

dependencies {
    // The Minecraft-free half, via the `core` composite build. ADR-0001.
    implementation("net.nennneko5787.sweetcookie:format")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.encoding = "UTF-8"
}

// Stonecutter rewrites sources in place; ModDevGradle must not race it.
tasks.named("createMinecraftArtifacts") {
    dependsOn(tasks.named("stonecutterGenerate"))
}
