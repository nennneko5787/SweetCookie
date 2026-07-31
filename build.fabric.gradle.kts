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

// ModMenu is a SOFT dependency (SC-280 section 3): compiled against, never required at runtime.
// The version is per Minecraft version because ModMenu tracks them one major line each - read out
// of each jar fabric.mod.json rather than inferred from the version number:
//   17.0.0        minecraft >=1.21.11 <26
//   20.0.0-beta.2 minecraft >=26.2-
// The API itself does not diverge: ModMenuApi.getModConfigScreenFactory has the same shape in both.
val modMenu = mapOf(
    "26.2" to "20.0.0-beta.2",
    "1.21.11" to "17.0.0",
)[mc] ?: error("no ModMenu version recorded for $mc")

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

        // The VERSION axis, named by version exactly as the loader axis above is named by loader.
        // A directory rather than //? comments, per SC-220 section 3.
        java.srcDir(rootProject.file("src/$mc/java"))
    }
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://maven.terraformersmc.com/releases") { name = "Terraformers (ModMenu)" }
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
    implementation("net.nennneko5787.sweetcookie:ui")

    // compileOnly: absent ModMenu must not break anything (SC-280 section 3). The entry point is
    // declared in fabric.mod.json and is only ever loaded BY ModMenu, so a client without it never
    // touches the class.
    modCompileOnly("com.terraformersmc:modmenu:$modMenu")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    // Deprecation warnings are on because they found a real one: NeoForge deprecates
    // Pack.Metadata's canonical constructor and Fabric does not, which is a loader divergence
    // worth being told about rather than one to discover from a removal.
    options.compilerArgs.add("-Xlint:deprecation")
    options.encoding = "UTF-8"
}
