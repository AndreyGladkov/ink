package io.github.andreygladkov.inkcalendar.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkPagedActivity
import io.github.andreygladkov.ink.ui.InkPagedItems
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.ink.ui.PermissionGate
import io.github.andreygladkov.inkcalendar.R
import io.github.andreygladkov.inkcalendar.events.CalendarInfo
import io.github.andreygladkov.inkcalendar.events.EventStore
import io.github.andreygladkov.inkcalendar.events.EventWriter
import io.github.andreygladkov.inkcalendar.remind.Reminders
import io.github.andreygladkov.inkcalendar.tasks.InkTasks
import io.github.andreygladkov.inkcalendar.widget.WidgetUpdater
import io.github.andreygladkov.ink.R as InkR

class SettingsActivity : InkPagedActivity() {
    override fun headerTitle(): String = getString(InkR.string.settings)

    private val tasksGate by lazy { PermissionGate(this, InkTasks.PERMISSION, REQUEST_TASKS) }

    override fun outsideState(): Any = calendars() to InkTasks(this).let { it.isInstalled to it.isGranted }

    override fun onResume() {
        super.onResume()
        WidgetUpdater.noticeTasksAccess(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_TASKS) rebuild()
    }

    override fun InkPagedItems.build() {
        val activity = this@SettingsActivity
        val calendars = calendars()
        section(getString(R.string.settings_calendars))
        if (calendars.isEmpty()) item { InkSettings.hint(activity, getString(R.string.no_calendars)) }
        val hidden = EventStore.hidden(activity)
        calendars.forEach { calendar ->
            item {
                InkChoiceRow(activity, calendar.name, listOf(true to getString(InkR.string.on), false to getString(InkR.string.off)),
                    calendar.id !in hidden) { shown -> show(calendar, shown) }
            }
        }
        val writable = calendars.filter { it.writable }
        if (writable.size > 1) {
            section(getString(R.string.settings_new_events))
            val current = EventWriter.defaultCalendar(activity, writable)?.id
            writable.forEach { calendar ->
                item {
                    InkSettings.rowButton(activity, calendar.name) {
                        EventWriter.setDefaultCalendar(activity, calendar.id)
                        rebuild()
                    }.apply { isSelected = calendar.id == current }
                }
            }
        }
        section(getString(R.string.event_reminders))
        item { InkSettings.onOff(activity, R.string.settings_reminders, Reminders.isOn(activity)) { Reminders.setOn(activity, it) } }
        item { InkSettings.hint(activity, getString(R.string.reminders_hint)) }
        addTasks()
        section(getString(InkR.string.settings_display))
        listOf(InkSettings::textSize, InkSettings::weight, InkSettings::lists, InkSettings::cornerRadius, InkSettings::colors).forEach { item { it(activity) } }
        InkSettings.addHaptics(activity, this, listOf(HapticChannel.BUTTONS to getString(InkR.string.settings_haptic_buttons)))
        InkSettings.addAbout(activity, this, AboutActivity::class.java)
    }

    private fun InkPagedItems.addTasks() {
        val activity = this@SettingsActivity
        val tasks = InkTasks(activity)
        if (!tasks.isInstalled) return
        section(getString(R.string.settings_tasks))
        item { InkSettings.onOff(activity, R.string.settings_show_tasks, tasks.isOn) { showTasks(it) } }
        if (tasks.isOn && !tasks.isGranted) {
            item { InkSettings.hint(activity, getString(R.string.tasks_access_hint)) }
            item { InkSettings.rowButton(activity, "") {}.also(tasksGate::bind) }
        }
    }

    private fun showTasks(on: Boolean) {
        InkTasks(this).setOn(on)
        WidgetUpdater.tasksChanged(this)
        if (on) tasksGate.request()
        rebuild()
    }

    private fun calendars(): List<CalendarInfo> =
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) EventStore.of(this).calendars() else emptyList()

    private fun show(calendar: CalendarInfo, shown: Boolean) {
        EventStore.setShown(this, calendar.id, shown)
        WidgetUpdater.updateAllInBackground(this)
    }

    companion object {
        private const val REQUEST_TASKS = 1

        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
