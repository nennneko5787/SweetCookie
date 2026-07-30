plugins {
    `kotlin-dsl`
}

dependencies {
    // The ledger is YAML because it needs comments — "Mojang's docs say X but the vanilla pig
    // actually does Y" is exactly the kind of note that keeps a compatibility table honest, and
    // JSON cannot carry it.
    implementation("org.yaml:snakeyaml:2.3")
    // JSON Schema validation for spec/schemas/**. The ledger is YAML and the schemas are JSON
    // Schema, so the YAML is read through Jackson into the same tree model the validator walks.
    implementation("com.networknt:json-schema-validator:1.5.4")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2")
    // Reads @SpecImpl / @ProvesSpec out of compiled bytecode, so the traceability check does not
    // depend on parsing Java source.
    implementation("org.ow2.asm:asm:9.7.1")
}

dependencies {
    // The specification checks ARE the correctness gate, and they have already shipped two bugs
    // that made them silently pass: a scan that matched nothing, and a map keyed so that 7 of 833
    // ledger entries were dropped. A check that cannot fail is worse than no check, so these are
    // regression-tested like any other code.
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}
