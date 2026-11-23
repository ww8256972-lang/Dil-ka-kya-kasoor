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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // Required for Talsec freeRASP Android SDK
    maven { url = uri("https://europe-west3-maven.pkg.dev/talsec-artifact-repository/freerasp") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://download.agora.io/maven/") }
  }
}

rootProject.name = "Waqar Institute"

// Multi-Module Architecture per Master PRD Section 8.1
include(":app")
include(":ui-liquid-glass")
include(":core-crypto")
include(":native-cpp20")
include(":exam-engine")
include(":sandbox-runtime")
include(":security")
