// The JS host SPI and the @minecraft/* facade interfaces. SC-200.
//
// NO JAVASCRIPT ENGINE HERE. GraalJS ships in a separate, optional companion mod (ADR-0005), so
// that users who only want JSON add-ons do not download it. This module declares the interface the
// companion provides.

dependencies {
    api(project(":api"))
    api(project(":format"))
}
