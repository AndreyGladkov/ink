package io.github.andreygladkov.inkweather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.os.BundleCompat
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.inkweather.MainActivity
import io.github.andreygladkov.inkweather.R
import io.github.andreygladkov.inkweather.forecast.ForecastCache
import io.github.andreygladkov.inkweather.forecast.Place
import io.github.andreygladkov.inkweather.forecast.Weather
import io.github.andreygladkov.inkweather.forecast.WeatherFormat
import io.github.andreygladkov.inkweather.places.PlaceStore
import kotlin.math.roundToInt

/** Draws widgets on the caller's thread: callers run it on [Weather.drawing] or [Weather.fetches], never on the main thread. */
object WidgetUpdater {
    private const val TAG = "WidgetUpdater"
    private val DEFAULT_SIZE_DP = SizeF(180f, 90f)

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, WeatherWidget::class.java))

    fun updateAll(context: Context, force: Boolean = false) = update(context, ids(context), force)

    fun updateFor(context: Context, placeId: Long) =
        update(context, PlaceStore.of(context).widgetsOf(placeId).toIntArray(), force = false)

    /** Without [force] a widget is redrawn only when what it shows has changed, since every redraw flashes the panel. */
    fun update(context: Context, ids: IntArray, force: Boolean) {
        if (ids.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        val store = PlaceStore.of(context)
        val cache = ForecastCache(context)
        val format = WeatherFormat(context)
        val style = InkStyle.of(context)
        val painter = WidgetPainter(context, style)
        val now = Weather.now()
        ids.forEach { id ->
            val place = store.widgetPlace(id)
            val content = WidgetContent.of(place, place?.let(cache::load), now, format)
            val size = sizeDp(manager.getAppWidgetOptions(id))
            val width = px(context, size.width)
            val height = px(context, size.height)
            val key = listOf(content, width, height, style).hashCode().toString()
            if (!force && store.drawn(id) == key) return@forEach
            try {
                manager.updateAppWidget(id, views(context, id, place, content, painter, width, height))
                store.setDrawn(id, key)
            } catch (failure: RuntimeException) {
                Log.w(TAG, "widget $id was not drawn", failure)
            }
        }
    }

    private fun views(context: Context, id: Int, place: Place?, content: WidgetContent, painter: WidgetPainter, width: Int, height: Int) =
        RemoteViews(context.packageName, R.layout.widget).apply {
            setImageViewBitmap(R.id.widget_image, painter.draw(content, width, height))
            setContentDescription(R.id.widget_image, listOfNotNull(content.place, content.now?.value, content.description).filter { it.isNotEmpty() }.joinToString(", "))
            setOnClickPendingIntent(R.id.widget_image, PendingIntent.getActivity(context, id, MainActivity.intent(context, place?.id, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }

    private fun sizeDp(options: Bundle): SizeF {
        BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)?.firstOrNull()?.let { return it }
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return if (width > 0 && height > 0) SizeF(width.toFloat(), height.toFloat()) else DEFAULT_SIZE_DP
    }

    private fun px(context: Context, dp: Float): Int {
        val metrics = context.resources.displayMetrics
        return (dp * metrics.density).roundToInt().coerceIn(1, maxOf(metrics.widthPixels, metrics.heightPixels))
    }
}
