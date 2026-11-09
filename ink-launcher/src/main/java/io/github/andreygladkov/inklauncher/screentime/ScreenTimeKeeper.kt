package io.github.andreygladkov.inklauncher.screentime

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.edit
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.inklauncher.LauncherPrefs
import java.time.Clock
import java.time.LocalDate

class UsagePeriod(private val days: List<Span>, private val log: UsageLog) {
    fun report(categories: AppCategories): ScreenTimeReport = ScreenTime.tally(log, days, categories)
}

/** The categories are kept by package, so every launcher entry of an app shares its category. */
class ScreenTimeKeeper(private val context: Context, private val clock: () -> Clock) {
    private val access = UsageAccess(context)
    private val prefs = context.inkPrefs()
    private var uncountedByDefault: Set<String>? = null
    private val keys = mapOf(
        AppCategory.DRIFTING to LauncherPrefs.DRIFTING_APPS,
        AppCategory.ON_PURPOSE to LauncherPrefs.ON_PURPOSE_APPS,
        AppCategory.UNCOUNTED to LauncherPrefs.UNCOUNTED_APPS,
    )

    val isGranted: Boolean get() = access.isGranted()

    fun requestAccess() = access.request()

    fun categories(): AppCategories = AppCategories(
        keys.flatMap { (category, key) -> stored(key).map { it to category } }.toMap(),
        uncountedByDefault(),
    )

    fun category(packageName: String): AppCategory = categories()[packageName]

    /** Choosing the default forgets the choice, so the app follows the default again and is no longer kept in the list. */
    fun setCategory(packageName: String, category: AppCategory) {
        val default = AppCategories(emptyMap(), uncountedByDefault())[packageName]
        prefs.edit {
            keys.forEach { (each, key) ->
                putStringSet(key, if (each == category && category != default) stored(key) + packageName else stored(key) - packageName)
            }
        }
    }

    fun forgetDefaults() {
        uncountedByDefault = null
    }

    private fun uncountedByDefault(): Set<String> = uncountedByDefault ?: (launchers() + keyboards()).also { uncountedByDefault = it }

    private fun stored(key: String): Set<String> = prefs.getStringSet(key, null).orEmpty().toSet()

    // AOSP Settings answers the home intent too, with FallbackHome at a negative priority, to cover the boot
    // before the user unlocks; it is not a launcher.
    private fun launchers(): Set<String> = context.packageManager
        .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
        .filter { it.priority >= 0 }
        .map { it.activityInfo.packageName }
        .toSet()

    // Only enabled keyboards: an app such as a password manager may ship a keyboard of its own that the user never
    // turned on, and the app itself should still count.
    private fun keyboards(): Set<String> =
        context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList.orEmpty().map { it.packageName }.toSet()

    fun period(dayCount: Int): UsagePeriod? {
        if (!isGranted) return null
        val clock = clock()
        val days = ScreenTime.days(LocalDate.now(clock), dayCount, clock.zone)
        return UsagePeriod(days, access.log(days.minOf { it.start } - LOOKBACK_MS, clock.millis()))
    }

    private companion object {
        // An app opened before midnight is found by the event that brought it to the front.
        const val LOOKBACK_MS = 6 * 60 * 60 * 1000L
    }
}
