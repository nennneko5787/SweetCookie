pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/")           { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases")    { name = "KikuGie" }
        maven("https://maven.kikugie.dev/snapshots")   { name = "KikuGie Snapshots" }
        maven("https://maven.parchmentmc.org")         { name = "ParchmentMC" }
    }
    // Convention plugins and the specification checks.
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.7"
}

rootProject.name = "Lepus"

// ── core/ is a SEPARATE BUILD ────────────────────────────────────────────────
// Not a subproject. A composite build makes ADR-0001 structural rather than
// aspirational: `core` has its own settings file, its own repositories and no
// access to anything Minecraft-related. A parser that reaches for net.minecraft.*
// cannot compile, which is the only kind of architectural boundary that survives.
includeBuild("core")

// ── The Stonecutter tree ─────────────────────────────────────────────────────
// One node per (Minecraft version x loader). Each node is a single Gradle project
// with one shared source tree; the loader-specific half is added as an extra source
// directory by that node's buildscript, never by a subproject.
//
// Adding a Minecraft version is one `match(...)` argument plus one section in
// stonecutter.properties.toml. See SC-220 section 6.
stonecutter {
    create(rootProject) {
        fun match(version: String, vararg loaders: String) = loaders.forEach {
            version("$version-$it", version).buildscript = "build.$it.gradle.kts"
        }

        match("26.2", "fabric", "neoforge")
        match("1.21.11", "fabric", "neoforge")

        // The node checked out in git and opened in the IDE.
        vcsVersion = "26.2-fabric"
    }
}
