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
        maven("https://jitpack.io") // blessed-android-coroutines
    }
}

rootProject.name = "RevScope"

include(":app")
include(":core:obd")
include(":core:data")
include(":core:common")
include(":core:intelligence")
include(":feature:dashboard")
include(":feature:gear")
include(":feature:sensors")
include(":feature:dtc")
include(":feature:session")
include(":feature:vehicle")
include(":feature:settings")
