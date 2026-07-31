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

        // The VERSION axis, named by version exactly as the loader axis above is named by loader.
        // A directory rather than //? comments, per SC-220 section 3.
        java.srcDir(rootProject.file("src/$mc/java"))
    }
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
}

// The headless registration test does NOT run on NeoForge, and that is a NeoForge fact rather than
// a decision: it patches SharedConstants to ask FMLEnvironment.isProduction(), which throws "There
// is no current FML Loader" outside a launched game. Bootstrapping Minecraft from plain JUnit needs
// ModDevGradle unit-test harness, which is a bigger piece of work than this bought.
//
// Nothing is lost that matters. BlockPool and PoolBlock are in src/main/java - version-dependent,
// loader-free - and both bugs this test exists for were version-API bugs. Running it on both Fabric
// nodes covers 1.21.11 and 26.2, which is the axis they lived on.
tasks.named<Test>("test") {
    enabled = false
}

neoForge {
    version = neoforgeVersion

    mods {
        register("sweetcookie") {
            sourceSet(sourceSets["main"])
        }
    }

    // Loom puts Minecraft on every source set; ModDevGradle only on the ones it is told about. The
    // headless registration test needs the game on its classpath to construct a Block at all.
    addModdingDependenciesTo(sourceSets["test"])
}


// The Minecraft-free half has to travel WITH the mod. It is our own code and there is nowhere for a
// user to fetch it from, so `implementation` alone compiles and then dies at runtime with
// NoClassDefFoundError - which is exactly what the first real-environment test found.
//
// Merged rather than nested: core has no dependencies of its own, so there is nothing to isolate,
// and Fabric's jar-in-jar wants nested jars to be mods while NeoForge's JarJar is a different
// mechanism again. Copying the classes in is the one answer that is identical on both loaders.
val coreBundle = configurations.create("coreBundle") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
configurations.named("implementation") { extendsFrom(coreBundle) }

tasks.named<Jar>("jar") {
    // EXCLUDE, not FAIL: the core modules each carry their own MANIFEST.MF and module marker, and
    // the mod jar keeps the one it made.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(provider { coreBundle.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}

dependencies {
    // The Minecraft-free half, via the `core` composite build. ADR-0001. `format` brings `molang`
    // and `api` transitively; `registry` is named separately because it is a peer of `format`,
    // not something `format` depends on - SC-120 is allocation and persistence, not parsing.
    coreBundle("net.nennneko5787.sweetcookie:format")
    coreBundle("net.nennneko5787.sweetcookie:registry")
    coreBundle("net.nennneko5787.sweetcookie:ui")

    val junit = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    testImplementation(platform(junit.findLibrary("junit-bom").get()))
    testImplementation(junit.findLibrary("junit-jupiter").get())
    testRuntimeOnly(junit.findLibrary("junit-launcher").get())
}

// The Minecraft range is PINNED, not open-ended. See the Fabric buildscript for why.
tasks.named<ProcessResources>("processResources") {
    inputs.property("minecraftVersion", mc)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("minecraftVersion" to mc)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    // Deprecation warnings are on because they found a real one: NeoForge deprecates
    // Pack.Metadata's canonical constructor and Fabric does not, which is a loader divergence
    // worth being told about rather than one to discover from a removal.
    options.compilerArgs.add("-Xlint:deprecation")
    options.encoding = "UTF-8"
}

// Stonecutter rewrites sources in place; ModDevGradle must not race it.
tasks.named("createMinecraftArtifacts") {
    dependsOn(tasks.named("stonecutterGenerate"))
}

