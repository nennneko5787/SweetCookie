// Logical identifiers, the block slot pool and the world ledger. SC-120.
//
// Separate from :format because it is not parsing: it is allocation and persistence. And in core/
// rather than in the Minecraft-dependent tree because SC-120 governs ON-DISK FORMATS - getting it
// wrong corrupts worlds - and that is exactly the code that deserves a test loop measured in
// seconds rather than one that needs a Minecraft node to compile.

dependencies {
    api(project(":api"))
    api(project(":format"))
}
