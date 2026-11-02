pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "compose-showcase"

include(":app")
include(":core:data")
include(":core:ui")
include(":core:testing")
include(":feature:search")
include(":feature:shelf")
include(":benchmark")

// Awaitable bottom sheets: the compose-bottom-sheets library, pulled in as a git submodule.
include(":sheets")
project(":sheets").projectDir = file("third_party/compose-bottom-sheets/sheets")
