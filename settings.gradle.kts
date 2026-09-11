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
        maven {
            url = uri("https://api.chargeahead.julakali.org/maven/")
            credentials {
                username = providers.gradleProperty("chargeahead.maven.user").orNull
                password = providers.gradleProperty("chargeahead.maven.password").orNull
            }
        }
    }
}

rootProject.name = "autoapp"

include(":shared")
include(":androidApp")
