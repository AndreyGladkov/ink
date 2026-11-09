package io.github.andreygladkov.inklauncher.screentime

enum class AppCategory { DRIFTING, ON_PURPOSE, UNCOUNTED }

/** Launchers and keyboards only lead to other apps, so their time is left out until the user chooses otherwise. */
class AppCategories(private val chosen: Map<String, AppCategory>, private val uncountedByDefault: Set<String>) {
    operator fun get(packageName: String): AppCategory =
        chosen[packageName] ?: if (packageName in uncountedByDefault) AppCategory.UNCOUNTED else AppCategory.ON_PURPOSE

    val chosenApps: Set<String> get() = chosen.keys
}
