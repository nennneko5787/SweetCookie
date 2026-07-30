// The Minecraft-free half of SweetCookie, as its own build.
//
// This is a SEPARATE BUILD rather than a set of subprojects, and that is the point: it has no
// access to the Minecraft repositories, no loader plugins and no Stonecutter. A parser that
// reaches for net.minecraft.* cannot compile. Constitution rule 3, ADR-0001.
//
// Nothing here may depend on the outer build. The dependency runs one way only.

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://repo.unnamed.team/repository/unnamed-public/") { name = "Unnamed (mocha)" }
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "core"

include(":api")       // @SpecImpl / @ProvesSpec annotations
include(":format")    // archive, manifest, JSON -> IR. The largest module in the project.
include(":molang")    // mocha binding, 315 query SPI, 106 filter tests
include(":script")    // JS host SPI and @minecraft/* facade interfaces
include(":testkit")   // conformance case loader, trace comparator, goldens
