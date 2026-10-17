pluginManagement {
    includeBuild("build-logic")
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
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
//@if sdk
        // KMP SDK published to GitHub Packages. Credentials: env GPR_USER / GPR_API_KEY, or
        // gpr.user / gpr.key in ~/.gradle/gradle.properties (never commit them).
        maven {
            name = "SdkPackages"
            url = uri("__SDK_REPO_URL__")
            credentials {
                username = System.getenv("GPR_USER") ?: providers.gradleProperty("gpr.user").orNull ?: ""
                password = System.getenv("GPR_API_KEY") ?: providers.gradleProperty("gpr.key").orNull ?: ""
            }
        }
//@endif
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "__PROJECT_NAME__"
include(":app")
include(":core:common")
include(":core:ui")
include(":core:navigation")
include(":core:testing")
//@if native
include(":core:network")
include(":core:database")
include(":core:datastore")
//@endif
//@if sdk
include(":core:sdk")
//@endif
// tezca-android:features (new-feature adds `include(":feature:<name>")` below this line)
