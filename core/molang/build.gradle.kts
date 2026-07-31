// Molang: parsing, compilation and the 315 query bindings. SC-130.
//
// Evaluated per bone, per frame, per visible entity, so the execution strategy — not the language
// surface — decides whether the client is playable. mocha compiles to JVM bytecode rather than
// walking an AST (ADR-0008), behind a facade so it can be replaced.

dependencies {
    api(project(":api"))
    api(project(":format"))
    implementation(libs.mocha)
}

