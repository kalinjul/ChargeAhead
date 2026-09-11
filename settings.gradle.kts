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

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // The contract module, served by the backend itself. Credentials come
        // from ~/.gradle/gradle.properties on a developer machine and from the
        // environment in CI - never from this repository.
        maven {
            url = uri("https://api.chargeahead.julakali.org/maven/")
            credentials {
                username = providers.gradleProperty("chargeahead.maven.user")
                    .orElse(providers.environmentVariable("CHARGEAHEAD_MAVEN_USER")).orNull
                password = providers.gradleProperty("chargeahead.maven.password")
                    .orElse(providers.environmentVariable("CHARGEAHEAD_MAVEN_PASSWORD")).orNull
            }
        }
    }
}

rootProject.name = "autoapp"

include(":shared")
include(":androidApp")
