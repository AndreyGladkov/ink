package io.github.andreygladkov.inktasks.calendar

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.core.content.edit
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.inktasks.store.TaskLists

/**
 * Open tasks with a due date, read-only, for Ink Calendar. The calendar is signed with another key, so the provider is
 * guarded by a dangerous permission the user grants it rather than by a signature permission.
 */
class TasksProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val context = context ?: return null
        require(uri == uri(context)) { "Unknown $uri" }
        val cursor = MatrixCursor(COLUMNS)
        DueTask.all(TaskLists.of(context)).forEach { task ->
            cursor.addRow(arrayOf<Any?>(task.list, task.ref.line, task.ref.occurrence, task.title, task.due.toString(), task.reminder?.toString()))
        }
        cursor.setNotificationUri(context.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.${uri.authority}.$PATH"

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        private const val PATH = "due"
        private const val PREF_TOLD = "calendar_told_due"
        private val COLUMNS = arrayOf("list", "line", "occurrence", "title", "due", "reminder")

        fun uri(context: Context): Uri = Uri.Builder().scheme("content").authority("${context.packageName}.tasks").appendPath(PATH).build()

        /** Only what was last told is kept, so a check that finds nothing new does not wake the calendar's widget. */
        fun notifyIfChanged(context: Context, due: List<DueTask>) {
            val prefs = context.inkPrefs()
            val told = due.hashCode()
            if (prefs.contains(PREF_TOLD) && prefs.getInt(PREF_TOLD, 0) == told) return
            prefs.edit { putInt(PREF_TOLD, told) }
            context.contentResolver.notifyChange(uri(context), null)
        }
    }
}
