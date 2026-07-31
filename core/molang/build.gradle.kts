// Molang: lexer, parser, folding, closure compilation and the 61 math functions. SC-130.
//
// NO third-party dependency and NO dependency on :format. ADR-0013: the pipeline is ours because
// Bedrock's Molang is float-typed and every available library evaluates in double, which changes
// which branch a pack takes.
//
// The absence of :format is deliberate too. This module reports a syntax failure as an exception
// carrying a line and column, and :format turns that into a diagnostic with provenance. Depending
// the other way would put a JSON facade and a pack model behind an expression compiler that needs
// neither.

dependencies {
    api(project(":api"))
}
