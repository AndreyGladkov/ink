dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ink"
include(":ink-ui")
include(":ink-phone")
include(":ink-messages")
include(":ink-clock")
include(":ink-calculator")
include(":ink-launcher")
include(":ink-weather")
