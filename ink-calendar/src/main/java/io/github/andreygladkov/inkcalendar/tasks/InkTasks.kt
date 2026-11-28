package io.github.andreygladkov.inkcalendar.tasks

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.inkcalendar.BuildConfig
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/** Open tasks with a due date that Ink Tasks gives the calendar once the user allows it. */
class InkTasks(private val context: Context) {
    val isInstalled: Boolean
        get() = try {
            context.packageManager.getPackageInfo(PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    val isGranted: Boolean get() = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    val isOn: Boolean get() = context.inkPrefs().getBoolean(PREF_SHOWN, true)

    val canRead: Boolean get() = isOn && isGranted

    fun setOn(on: Boolean) = context.inkPrefs().edit { putBoolean(PREF_SHOWN, on) }

    /** Ink Tasks can be removed, or its permission revoked, between the check and the query. */
    fun due(): List<DueTask> {
        if (!canRead) return emptyList()
        return try {
            context.contentResolver.query(URI, null, null, null, null)?.use(::read).orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    private fun read(cursor: Cursor): List<DueTask> = buildList {
        val list = cursor.getColumnIndexOrThrow("list")
        val line = cursor.getColumnIndexOrThrow("line")
        val occurrence = cursor.getColumnIndexOrThrow("occurrence")
        val title = cursor.getColumnIndexOrThrow("title")
        val due = cursor.getColumnIndexOrThrow("due")
        val reminder = cursor.getColumnIndexOrThrow("reminder")
        while (cursor.moveToNext()) {
            val date = parse(cursor.getString(due), LocalDate::parse) ?: continue
            add(DueTask(cursor.getString(list).orEmpty(), cursor.getString(line).orEmpty(), cursor.getInt(occurrence), cursor.getString(title).orEmpty(),
                date, cursor.getString(reminder)?.let { parse(it, LocalDateTime::parse) }))
        }
    }

    private fun <T> parse(text: String?, parser: (String) -> T): T? = try {
        text?.let(parser)
    } catch (_: DateTimeParseException) {
        null
    }

    companion object {
        val PACKAGE: String = BuildConfig.TASKS_PACKAGE
        val PERMISSION = "$PACKAGE.permission.READ_TASKS"
        val URI: Uri = "content://$PACKAGE.tasks/due".toUri()
        private const val ACTION_OPEN = "io.github.andreygladkov.inktasks.action.OPEN_TASK"
        private const val PREF_SHOWN = "show_ink_tasks"

        fun open(task: DueTask): Intent = Intent(ACTION_OPEN).setPackage(PACKAGE)
            .putExtra("list", task.list)
            .putExtra("line", task.line)
            .putExtra("occurrence", task.occurrence)
    }
}
