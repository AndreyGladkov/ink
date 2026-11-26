package io.github.andreygladkov.inkcalendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class CalendarWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in REDRAWS) return super.onReceive(context, intent)
        val ids = WidgetUpdater.ids(context)
        if (ids.isNotEmpty()) ChangesJob.schedule(context)
        inBackground { WidgetUpdater.update(context, ids, force = false) }
    }

    override fun onEnabled(context: Context) = ChangesJob.schedule(context)

    override fun onDisabled(context: Context) {
        ChangesJob.cancel(context)
        WidgetUpdater.cancelTick(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ChangesJob.schedule(context)
        inBackground { WidgetUpdater.update(context, ids, force = true) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        inBackground { WidgetUpdater.update(context, intArrayOf(id), force = false) }

    override fun onDeleted(context: Context, ids: IntArray) = WidgetUpdater.forget(context, ids)

    private fun inBackground(work: () -> Unit) {
        val pending = goAsync()
        WidgetUpdater.drawing.execute {
            try {
                work()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TICK = "io.github.andreygladkov.inkcalendar.widget.TICK"
        private val REDRAWS = setOf(ACTION_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
