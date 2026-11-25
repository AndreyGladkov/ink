package io.github.andreygladkov.inktasks.store

import android.content.Context
import androidx.core.content.edit
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.inktasks.R
import io.github.andreygladkov.inktasks.remind.Reminders
import io.github.andreygladkov.inktasks.tasks.TaskFile
import java.io.IOException
import java.text.Collator
import java.time.Clock
import java.time.LocalDate

/**
 * The lists in the app's folder. Every change reads the file anew, applies that one change and writes it back at once,
 * so whatever a sync brought in since the screen was drawn is kept.
 */
class TaskLists(private val context: Context, private val folder: Folder) {
    enum class Sort { MANUAL, DUE }

    private val prefs get() = context.inkPrefs()

    val isAvailable: Boolean get() = folder.isAvailable

    fun names(): List<String> {
        val collator = Collator.getInstance()
        return attempt { folder.names() }.orEmpty().sortedWith { a, b -> collator.compare(a, b) }
    }

    /** The list opened last; until a list has a file, the default list that its first task will create. */
    val current: String get() = current(names())

    /** [current] out of [names] already read, so a screen that shows them too lists the folder once. */
    fun current(names: List<String>): String =
        prefs.getString(PREF_CURRENT, null)?.takeIf { it in names } ?: names.firstOrNull() ?: context.getString(R.string.default_list)

    fun open(name: String) = prefs.edit { putString(PREF_CURRENT, name) }

    fun read(name: String): TaskFile = TaskFile.parse(attempt { folder.read(name) }.orEmpty())

    /** Null when the task is gone or the folder can no longer be written, which the screens treat alike. */
    fun change(name: String, change: (TaskFile) -> TaskFile.Change?): TaskFile.Change? {
        val text = attempt { folder.read(name) } ?: if (attempt { folder.names() } == null) return null else ""
        val before = TaskFile.parse(text)
        val changed = change(before) ?: return null
        attempt { folder.write(name, changed.file.toText()) } ?: return null
        if (Reminders.inFile(name, before) != Reminders.inFile(name, changed.file)) Reminders.update(context)
        return changed
    }

    fun sort(name: String): Sort = if (prefs.getBoolean(sortKey(name), false)) Sort.DUE else Sort.MANUAL

    fun setSort(name: String, sort: Sort) = prefs.edit { putBoolean(sortKey(name), sort == Sort.DUE) }

    /** Obsidian vaults are often synced to case-insensitive file systems, where "Home" and "home" are one file. */
    fun exists(name: String) = names().any { it.equals(name, ignoreCase = true) }

    fun create(name: String): Boolean = !exists(name) && attempt { folder.create(name) } == true

    fun rename(from: String, to: String): Boolean {
        if (from == to) return true
        if (names().any { it.equals(to, ignoreCase = true) && !it.equals(from, ignoreCase = true) }) return false
        if (!renameFile(from, to)) return false
        Reminders.update(context)
        val wasCurrent = prefs.getString(PREF_CURRENT, null) == from
        val sort = sort(from)
        prefs.edit {
            remove(sortKey(from))
            if (sort == Sort.DUE) putBoolean(sortKey(to), true)
            if (wasCurrent) putString(PREF_CURRENT, to)
        }
        return true
    }

    /** A case-insensitive file system takes a rename that only changes case as a rename to itself and keeps the old name. */
    private fun renameFile(from: String, to: String): Boolean {
        if (!from.equals(to, ignoreCase = true)) return attempt { folder.rename(from, to) } == true
        val step = "$to$CASE_STEP"
        if (attempt { folder.rename(from, step) } != true) return false
        if (attempt { folder.rename(step, to) } == true) return true
        attempt { folder.rename(step, from) }
        return false
    }

    fun delete(name: String) {
        attempt { folder.delete(name) }
        prefs.edit { remove(sortKey(name)) }
        Reminders.update(context)
    }

    private fun sortKey(name: String) = PREF_SORT_PREFIX + name

    /** A chosen folder can vanish or have its permission revoked at any moment, which a provider reports by throwing. */
    private fun <T> attempt(action: () -> T): T? = try {
        action()
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    companion object {
        private const val PREF_CURRENT = "current_list"
        private const val PREF_SORT_PREFIX = "sort_by_due:"
        private const val CASE_STEP = " (renaming)"

        var fixedClock: Clock? = null

        /** A system clock keeps the time zone it was made in, so it is made anew to follow the phone into another zone. */
        val clock: Clock get() = fixedClock ?: Clock.systemDefaultZone()

        fun today(): LocalDate = LocalDate.now(clock)

        fun of(context: Context) = TaskLists(context, Storage(context).folder)
    }
}
