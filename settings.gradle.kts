pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "lumbina"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":baselineprofile")

include(":core:domain")
include(":core:common")
include(":core:network")
include(":core:data")
include(":core:designsystem")
include(":core:ui")
include(":core:testing")

include(":feature:home")
include(":feature:settings")
include(":feature:onboarding")
include(":feature:screensaver")

include(":platform:packages")
include(":platform:accessibility")
include(":platform:system")
include(":platform:weather")
include(":platform:wallpaper")
include(":platform:tvprovider")
include(":platform:adb")

include(":speech:vosk")
include(":speech:cloud")

include(":vendor:generic")
include(":vendor:hisense")


include(":companion:tvprovider")

// A module of local experiments, not part of the repository: built only on request and only
// where the folder exists (./gradlew -Plab :lab:assembleDebug).
if (providers.gradleProperty("lab").isPresent && file("lab").exists()) include(":lab")
