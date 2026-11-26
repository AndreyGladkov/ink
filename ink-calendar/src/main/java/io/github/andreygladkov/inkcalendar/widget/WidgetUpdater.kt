package io.github.andreygladkov.inkcalendar.widget

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.inkcalendar.MainActivity
import io.github.andreygladkov.inkcalendar.R
import io.github.andreygladkov.inkcalendar.events.CalendarClock
import io.github.andreygladkov.inkcalendar.events.EventFormat
import io.github.andreygladkov.inkcalendar.events.EventStore
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Draws widgets on the caller's thread: callers run it on [drawing], never on the main thread. */
object WidgetUpdater {
    private const val TAG = "WidgetUpdater"
    private const val PREF_DRAWN = "widget_drawn_"
    private val DEFAULT_SIZE_DP = SizeF(180f, 90f)
    val drawing: Executor = Executors.newSingleThreadExecutor()

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, CalendarWidget::class.java))

    fun updateAllInBackground(context: Context) {
        val app = context.applicationContext
        drawing.execute { update(app, ids(app), force = false) }
    }

    /** Without [force] a widget is redrawn only when what it shows has changed, since every redraw flashes the panel. */
    fun update(context: Context, ids: IntArray, force: Boolean) {
        if (ids.isEmpty()) return cancelTick(context)
        val manager = AppWidgetManager.getInstance(context)
        val zone = CalendarClock.zone
        val now = CalendarClock.now()
        val today = now.atZone(zone).toLocalDate()
        val occurrences = if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            EventStore.of(context).occurrences(today, today.plusDays(2), zone)
        } else {
            null
        }
        val content = WidgetContent.of(context, now, zone, occurrences, EventFormat(context, zone))
        val style = InkStyle.of(context)
        val painter = WidgetPainter(context, style)
        val prefs = context.inkPrefs()
        ids.forEach { id ->
            val size = sizeDp(manager.getAppWidgetOptions(id))
            val width = px(context, size.width)
            val height = px(context, size.height)
            val key = listOf(content, width, height, style).hashCode().toString()
            if (!force && prefs.getString(PREF_DRAWN + id, null) == key) return@forEach
            try {
                manager.updateAppWidget(id, views(context, content, painter, width, height))
                prefs.edit { putString(PREF_DRAWN + id, key) }
            } catch (failure: RuntimeException) {
                Log.w(TAG, "widget $id was not drawn", failure)
            }
        }
        scheduleTick(context, WidgetContent.nextChange(now, zone, occurrences).toEpochMilli())
    }

    fun forget(context: Context, ids: IntArray) = context.inkPrefs().edit { ids.forEach { remove(PREF_DRAWN + it) } }

    private fun views(context: Context, content: WidgetContent, painter: WidgetPainter, width: Int, height: Int) =
        RemoteViews(context.packageName, R.layout.widget).apply {
            setImageViewBitmap(R.id.widget_image, painter.draw(content, width, height))
            setContentDescription(R.id.widget_image, listOfNotNull(content.weekday, "${content.day} ${content.month}", content.note).joinToString(", "))
            setOnClickPendingIntent(R.id.widget_image, PendingIntent.getActivity(context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
        }

    /** Inexact on purpose: a widget a few minutes late costs nothing, and an exact alarm needs a permission. */
    private fun scheduleTick(context: Context, at: Long) =
        context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, at, tick(context))

    fun cancelTick(context: Context) = context.getSystemService(AlarmManager::class.java).cancel(tick(context))

    private fun tick(context: Context) = PendingIntent.getBroadcast(context, 0,
        Intent(context, CalendarWidget::class.java).setAction(CalendarWidget.ACTION_TICK), PendingIntent.FLAG_IMMUTABLE)

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
