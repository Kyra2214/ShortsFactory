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

rootProject.name = "ShortsFactory"
include(":app")
include(":core")
include(":data")
include(":domain")
include(":video-engine")
include(":feature-home")
include(":feature-projects")
include(":feature-editor")
include(":feature-ai")
include(":feature-export")
include(":feature-settings")
include(":feature-trends")
include(":feature-player")
