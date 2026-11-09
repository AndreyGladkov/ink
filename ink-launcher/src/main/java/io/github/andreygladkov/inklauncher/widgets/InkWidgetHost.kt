package io.github.andreygladkov.inklauncher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import io.github.andreygladkov.inklauncher.home.AppKey
import kotlin.math.ceil

class InkWidgetHost(private val context: Context) : AppWidgetHost(context, HOST_ID) {
    private val manager = AppWidgetManager.getInstance(context)
    private val views = HashMap<Int, InkWidgetView>()

    var onLongPress: ((widgetId: Int) -> Unit)? = null

    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        InkWidgetView(context) { onLongPress?.invoke(appWidgetId) }

    /** The same view is kept for a widget while the screen is rebuilt, so it does not reload. */
    fun view(widgetId: Int): InkWidgetView? {
        views[widgetId]?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            return view
        }
        val info = manager.getAppWidgetInfo(widgetId) ?: return null
        return (createView(context, widgetId, info) as InkWidgetView).also { views[widgetId] = it }
    }

    fun providers(): List<AppWidgetProviderInfo> = manager.installedProviders

    fun info(widgetId: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(widgetId)

    /** The configure activity must still exist and be open to the launcher, or Configure… would do nothing. */
    fun isReconfigurable(widgetId: Int): Boolean {
        val info = info(widgetId) ?: return false
        if ((info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) == 0) return false
        val configure = info.configure ?: return false
        return runCatching { context.packageManager.getActivityInfo(configure, 0).exported }.getOrDefault(false)
    }

    fun bindIfAllowed(widgetId: Int, provider: ComponentName): Boolean = manager.bindAppWidgetIdIfAllowed(widgetId, provider)

    fun remove(widgetId: Int) {
        views.remove(widgetId)
        deleteAppWidgetId(widgetId)
    }

    companion object {
        const val HOST_ID = 1024

        fun bindIntent(widgetId: Int, provider: ComponentName) = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)

        fun span(minPx: Int, cellPx: Int, cells: Int) = ceil(minPx.toDouble() / cellPx.coerceAtLeast(1)).toInt().coerceIn(1, cells.coerceAtLeast(1))

        fun key(info: AppWidgetProviderInfo) = AppKey(info.provider.packageName, info.provider.className)
    }
}
