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
        // Agora SDK — uncomment if you pull the RTC/RTM SDKs via Maven instead of a local AAR.
        // maven { url = uri("https://download.agora.io/sdk/release/maven") }
    }
}

rootProject.name = "live-lens-games"
include(":app")
