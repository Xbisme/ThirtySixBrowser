pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // EXCEPTION to FR-002 (no version literals): settings-level plugins are resolved
    // BEFORE the version catalog is loaded — referencing `libs.versions.foojay` here
    // would create a chicken-and-egg. Inline literal is permitted for settings plugins
    // only. See spec.md FR-002 note.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ThirtySixBrowser"
include(":app")
