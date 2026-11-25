package io.github.andreygladkov.inktasks.remind

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.andreygladkov.inktasks.store.TaskLists
import io.github.andreygladkov.inktasks.tasks.TaskFile
import io.github.andreygladkov.inktasks.tasks.TaskRef
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DONE -> change(context, intent) { file, ref -> file.setDone(ref, true, TaskLists.today()) }
            ACTION_LATER -> change(context, intent) { file, ref ->
                file.setReminder(ref, LocalDateTime.now(TaskLists.clock).plusHours(1).truncatedTo(ChronoUnit.MINUTES))
            }
            in CHECK_ACTIONS -> Reminders.update(context)
        }
    }

    private fun change(context: Context, intent: Intent, change: (TaskFile, TaskRef) -> TaskFile.Change?) {
        val list = intent.getStringExtra(EXTRA_LIST) ?: return
        val ref = TaskRef(intent.getStringExtra(EXTRA_LINE) ?: return, intent.getIntExtra(EXTRA_OCCURRENCE, 0))
        TaskLists.of(context).change(list) { change(it, ref) } ?: Reminders.update(context)
    }

    companion object {
        internal const val ACTION_DONE = "io.github.andreygladkov.inktasks.action.REMINDER_DONE"
        internal const val ACTION_LATER = "io.github.andreygladkov.inktasks.action.REMINDER_LATER"
        private const val EXTRA_LIST = "list"
        private const val EXTRA_LINE = "line"
        private const val EXTRA_OCCURRENCE = "occurrence"
        private val CHECK_ACTIONS = setOf(
            Reminders.ACTION_CHECK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )

        fun intent(context: Context, action: String, reminder: Reminder): Intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_LIST, reminder.list)
            .putExtra(EXTRA_LINE, reminder.ref.line)
            .putExtra(EXTRA_OCCURRENCE, reminder.ref.occurrence)
    }
}
