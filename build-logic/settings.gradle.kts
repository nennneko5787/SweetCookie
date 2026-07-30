// Convention plugins and the specification checks. An included build, so it compiles once and is
// available to both the root build and the `core` build without either depending on the other.

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
