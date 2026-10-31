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

// Awaitable bottom sheets: the compose-bottom-sheets library, pulled in as a git submodule.
include(":sheets")
project(":sheets").projectDir = file("third_party/compose-bottom-sheets/sheets")
