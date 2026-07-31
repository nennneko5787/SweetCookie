plugins {
    id("java")
    // Picks fabric-loom-remap on obfuscated nodes and fabric-loom on unobfuscated ones,
    // so one buildscript spans the 26.1 deobfuscation seam. See gradle.properties.
    id("dev.kikugie.loom-back-compat")
}

val mc: String = stonecutter.current.version
val javaVersion = if (stonecutter.eval(mc, ">=26.1")) 25 else 21

// TODO(spike): move to stonecutter.properties.toml once the toml property API is confirmed.
val fabricApi = mapOf(
    "26.2" to "0.156.0+26.2",
    "1.21.11" to "0.141.6+1.21.11",
)[mc] ?: error("no fabric-api version recorded for $mc")
val fabricLoader = "0.19.3"

group = "net.nennneko5787"
// One file per (Minecraft version x loader), which is what Modrinth and CurseForge expect.
version = "0.1.0+$mc-fabric"
base.archivesName = "sweetcookie"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

sourceSets {
    named("main") {
        // Loader-specific code lives in its own directory, not behind //? comments.
        // Constitution rule 12, SC-220 section 3.
        java.srcDir(rootProject.file("src/fabric/java"))
        resources.srcDir(rootProject.file("src/fabric/resources"))
    }
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    // A no-op on unobfuscated nodes; the plugin swallows it rather than us branching.
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:$fabricLoader")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApi")

    // The Minecraft-free half, via the `core` composite build. ADR-0001. `format` brings `molang`
    // and `api` transitively; `registry` is named separately because it is a peer of `format`,
    // not something `format` depends on - SC-120 is allocation and persistence, not parsing.
    implementation("net.nennneko5787.sweetcookie:format")
    implementation("net.nennneko5787.sweetcookie:registry")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.encoding = "UTF-8"
}
