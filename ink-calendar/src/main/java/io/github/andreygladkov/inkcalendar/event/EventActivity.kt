package io.github.andreygladkov.inkcalendar.event

import android.content.Context
import android.content.Intent
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkPagedActivity
import io.github.andreygladkov.ink.ui.InkPagedItems
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.confirmBeforeAction
import io.github.andreygladkov.inkcalendar.R
import io.github.andreygladkov.inkcalendar.events.CalendarClock
import io.github.andreygladkov.inkcalendar.events.EventFormat
import io.github.andreygladkov.inkcalendar.events.EventStore
import io.github.andreygladkov.inkcalendar.events.EventWriter
import io.github.andreygladkov.inkcalendar.events.Occurrence

class EventActivity : InkPagedActivity() {
    private data class Shown(val occurrence: Occurrence?, val reminders: List<Int>)

    private val eventId get() = intent.getLongExtra(EXTRA_EVENT, -1)
    private val begin get() = intent.getLongExtra(EXTRA_BEGIN, 0)

    override fun headerTitle(): String = getString(R.string.event)

    override fun outsideState(): Any = load()

    override fun InkPagedItems.build() {
        val (occurrence, reminders) = load()
        if (occurrence == null) return item { text(getString(R.string.event_gone)) }
        val format = EventFormat(this@EventActivity)
        item {
            text(occurrence.title.ifBlank { getString(R.string.no_title) }).apply {
                textScale = InkStyle.TITLE_SCALE
                isBold = true
            }
        }
        format.span(occurrence).forEach { line -> item { text(line) } }
        if (occurrence.repeats) item { InkSettings.hint(this@EventActivity, getString(R.string.event_repeats)) }
        section(getString(R.string.event_calendar))
        item { text(occurrence.calendarName) }
        if (occurrence.location.isNotBlank()) {
            section(getString(R.string.event_location))
            item { text(occurrence.location) }
        }
        if (occurrence.notes.isNotBlank()) {
            section(getString(R.string.event_notes))
            item { text(occurrence.notes.trim()) }
        }
        if (reminders.isNotEmpty()) {
            section(getString(R.string.event_reminders))
            reminders.forEach { minutes -> item { text(format.reminder(minutes)) } }
        }
        if (occurrence.editable && !occurrence.repeats) {
            item { InkSettings.rowButton(this@EventActivity, getString(R.string.edit_event)) { edit() } }
            item {
                InkSettings.rowButton(this@EventActivity, getString(R.string.delete_event)) {}.apply {
                    confirmBeforeAction(R.string.delete_confirm) { delete() }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun edit() = startActivityForResult(EditActivity.edit(this, eventId, begin), REQUEST_EDIT)

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT) EditActivity.savedBegin(data)?.let { intent.putExtra(EXTRA_BEGIN, it) }
    }

    private fun delete() {
        EventWriter.of(this).delete(eventId)
        finish()
    }

    private fun load(): Shown {
        val store = EventStore(contentResolver, emptySet())
        val occurrence = store.occurrence(eventId, begin, CalendarClock.zone)
        return Shown(occurrence, if (occurrence == null) emptyList() else store.reminders(eventId))
    }

    private fun text(value: String) = InkTextView(this).apply { text = value }

    companion object {
        private const val EXTRA_EVENT = "event"
        private const val EXTRA_BEGIN = "begin"
        private const val REQUEST_EDIT = 1

        fun intent(context: Context, eventId: Long, begin: Long): Intent =
            Intent(context, EventActivity::class.java).putExtra(EXTRA_EVENT, eventId).putExtra(EXTRA_BEGIN, begin)
    }
}
