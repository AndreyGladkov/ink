package io.github.andreygladkov.inktasks.remind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.edit
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.inktasks.store.Storage
import io.github.andreygladkov.inktasks.store.TaskLists
import io.github.andreygladkov.inktasks.tasks.TaskFile
import io.github.andreygladkov.inktasks.tasks.TaskRef
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class Reminder(val list: String, val ref: TaskRef, val title: String, val at: LocalDateTime) {
    val key: String get() = listOf(list, ref.line, ref.occurrence.toString()).joinToString(KEY_SEPARATOR)

    fun instant(zone: ZoneId): Instant = at.atZone(zone).toInstant()

    private companion object {
        const val KEY_SEPARATOR = "\n"
    }
}

/**
 * Only the next reminder is handed to the system. Each check notifies the reminders that are due now and were not due at
 * the check before, so one set in the past, edited, or brought by a sync still comes once. Reminders live in files
 * that a sync can change at any time, so every check reads them all instead of keeping a copy.
 */
object Reminders {
    internal const val ACTION_CHECK = "io.github.andreygladkov.inktasks.action.CHECK_REMINDERS"
    private const val PREF_DUE_AT_LAST_CHECK = "reminders_due"
    private const val REQUEST_NEXT = 0
    private const val REQUEST_SCAN = 1
    private const val SCAN_INTERVAL_MS = AlarmManager.INTERVAL_HALF_HOUR

    fun inFile(list: String, file: TaskFile): List<Reminder> = file.tasks.filter { !it.isDone }.flatMap { task ->
        (listOf(task) + task.subtasks.filter { !it.isDone }).mapNotNull { open ->
            open.reminder?.let { Reminder(list, open.ref, open.title, it) }
        }
    }

    fun update(context: Context) {
        val lists = TaskLists.of(context)
        val inFolder = Storage(context).chosen != null
        scanInBackground(context, inFolder)
        if (!lists.isAvailable) return
        val clock = TaskLists.clock
        val now = clock.instant()
        val reminders = lists.names().flatMap { inFile(it, lists.read(it)) }
        val (due, coming) = reminders.partition { !it.instant(clock.zone).isAfter(now) }
        val dueKeys = due.map { it.key }.toSet()
        val prefs = context.inkPrefs()
        val dueBefore = prefs.getStringSet(PREF_DUE_AT_LAST_CHECK, null) ?: dueKeys
        due.filter { it.key !in dueBefore }.forEach { ReminderNotifications.show(context, it) }
        ReminderNotifications.keepOnly(context, dueKeys)
        prefs.edit { putStringSet(PREF_DUE_AT_LAST_CHECK, dueKeys) }
        scheduleNext(context, coming.minOfOrNull { it.instant(clock.zone) })
    }

    /** Without exact alarms, which the user can deny on Android 12, a reminder may come a few minutes late. */
    private fun scheduleNext(context: Context, at: Instant?) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val check = check(context, REQUEST_NEXT)
        when {
            at == null -> manager.cancel(check)
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms() ->
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), check)
            else -> manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), check)
        }
    }

    /**
     * A folder synced from a computer can bring a reminder the app has not seen, and its provider may not be ready yet
     * after a reboot, so it is read again now and then.
     */
    private fun scanInBackground(context: Context, isOn: Boolean) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val check = check(context, REQUEST_SCAN)
        if (isOn) {
            manager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + SCAN_INTERVAL_MS, SCAN_INTERVAL_MS, check)
        } else {
            manager.cancel(check)
        }
    }

    private fun check(context: Context, request: Int): PendingIntent = PendingIntent.getBroadcast(context, request,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_CHECK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
