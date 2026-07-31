// Archive access, manifest parsing and JSON -> IR. SC-100 and SC-110.
//
// The largest single body of code in the project, and it compiles in seconds because there is no
// Minecraft here. If you find yourself wanting net.minecraft.*, the IR is missing something —
// that is a bug in SC-110, not a reason to reach across the boundary.
//
// The dependency on :molang is what SC-110 §7 requires: a field Bedrock allows to be an expression
// is a compiled MolangExpr in the IR, never a string. :molang depends on nothing, so this direction
// pulls in no transitive weight and cannot cycle.

dependencies {
    api(project(":api"))
    api(project(":molang"))
}
