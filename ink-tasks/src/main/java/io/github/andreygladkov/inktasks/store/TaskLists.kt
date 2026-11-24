package io.github.andreygladkov.inktasks.store

import android.content.Context
import androidx.core.content.edit
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.inktasks.R
import io.github.andreygladkov.inktasks.tasks.TaskFile
import java.io.File
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

    fun names(): List<String> {
        val collator = Collator.getInstance()
        return folder.names().sortedWith { a, b -> collator.compare(a, b) }
    }

    /** The list opened last; until a list has a file, the default list that its first task will create. */
    val current: String
        get() {
            val names = names()
            return prefs.getString(PREF_CURRENT, null)?.takeIf { it in names } ?: names.firstOrNull() ?: context.getString(R.string.default_list)
        }

    fun open(name: String) = prefs.edit { putString(PREF_CURRENT, name) }

    fun read(name: String): TaskFile = TaskFile.parse(folder.read(name).orEmpty())

    fun change(name: String, change: (TaskFile) -> TaskFile.Change?): TaskFile.Change? {
        val changed = change(read(name)) ?: return null
        folder.write(name, changed.file.toText())
        return changed
    }

    fun sort(name: String): Sort = if (prefs.getBoolean(sortKey(name), false)) Sort.DUE else Sort.MANUAL

    fun setSort(name: String, sort: Sort) = prefs.edit { putBoolean(sortKey(name), sort == Sort.DUE) }

    /** Obsidian vaults are often synced to case-insensitive file systems, where "Home" and "home" are one file. */
    fun exists(name: String) = names().any { it.equals(name, ignoreCase = true) }

    fun create(name: String): Boolean = !exists(name) && folder.create(name)

    fun rename(from: String, to: String): Boolean {
        if (from == to) return true
        if (names().any { it.equals(to, ignoreCase = true) && !it.equals(from, ignoreCase = true) }) return false
        if (!renameFile(from, to)) return false
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
        if (!from.equals(to, ignoreCase = true)) return folder.rename(from, to)
        val step = "$to$CASE_STEP"
        if (!folder.rename(from, step)) return false
        if (folder.rename(step, to)) return true
        folder.rename(step, from)
        return false
    }

    fun delete(name: String) {
        folder.delete(name)
        prefs.edit { remove(sortKey(name)) }
    }

    private fun sortKey(name: String) = PREF_SORT_PREFIX + name

    companion object {
        internal const val DIR = "tasks"
        private const val PREF_CURRENT = "current_list"
        private const val PREF_SORT_PREFIX = "sort_by_due:"
        private const val CASE_STEP = " (renaming)"

        var clock: Clock = Clock.systemDefaultZone()

        fun today(): LocalDate = LocalDate.now(clock)

        fun of(context: Context) = TaskLists(context, LocalFolder(File(context.filesDir, DIR)))
    }
}
