pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "ApraxModsStore"

include(":app")
include(":core")
include(":domain")
include(":data")
include(":feature-home")
include(":feature-search")
include(":feature-categories")
include(":feature-details")
include(":feature-downloads")
include(":feature-library")
include(":feature-settings")
