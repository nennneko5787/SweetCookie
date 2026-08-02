plugins {
    id("lepus.spec")
    alias(libs.plugins.stonecutter)
    alias(libs.plugins.loom.back.compat).apply(false)
    // Declared but never applied here. loom-back-compat applies exactly one of them per node,
    // chosen by whether that node's Minecraft version is obfuscated.
    alias(libs.plugins.loom).apply(false)
    alias(libs.plugins.loom.remap).apply(false)
    alias(libs.plugins.moddev).apply(false)
}

stonecutter active file(".sc_active_version")

stonecutter parameters {
    // The loader is the part of the node name after the last '-'. This is what makes
    // `//? if fabric {` work in shared source. Per SC-220 section 2.3 it is for SMALL
    // divergences only; anything larger belongs in src/<loader>/java.
    constants.match(current.project.substringAfterLast('-'), "fabric", "neoforge")

    swaps["mod_id"]      = "\"${properties.get<String>("mod.id")}\";"
    swaps["mod_name"]    = "\"${properties.get<String>("mod.name")}\";"
    swaps["mod_version"] = "\"${properties.get<String>("mod.version")}\";"
    swaps["minecraft"]   = "\"${current.version}\";"
}

// Build every node. CI runs this on every push: a change may land on one combination
// first, but one that breaks another combination's build is rejected (SC-220 section 9).
tasks.register("chiseledBuild") {
    group = "lepus"
    description = "Build every (Minecraft version x loader) node."
    dependsOn(stonecutter.tasks.named("build"))
}

tasks.register("chiseledCompile") {
    group = "lepus"
    description = "Compile every node without packaging. The cheap CI gate."
    dependsOn(stonecutter.tasks.named("compileJava"))
}
