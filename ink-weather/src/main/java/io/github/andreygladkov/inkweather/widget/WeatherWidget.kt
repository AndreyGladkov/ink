package io.github.andreygladkov.inkweather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import io.github.andreygladkov.inkweather.forecast.Weather
import io.github.andreygladkov.inkweather.places.PlaceStore

class WeatherWidget : AppWidgetProvider() {
    override fun onEnabled(context: Context) = RefreshJob.schedule(context)

    override fun onDisabled(context: Context) = RefreshJob.cancel(context)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        RefreshJob.schedule(context)
        inBackground { WidgetUpdater.update(context, ids, force = true) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        inBackground { WidgetUpdater.update(context, intArrayOf(id), force = false) }

    override fun onDeleted(context: Context, ids: IntArray) = PlaceStore.of(context).unbind(ids)

    private fun inBackground(work: () -> Unit) {
        val pending = goAsync()
        Weather.drawing.execute {
            try {
                work()
            } finally {
                pending.finish()
            }
        }
    }
}
