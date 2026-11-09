package io.github.andreygladkov.inklauncher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.os.Bundle
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAppWidgetHost

/** Robolectric's own shadow leaves the widget service out, so starting a configure activity silently does nothing. */
@Implements(AppWidgetHost::class)
class RecordingAppWidgetHost : ShadowAppWidgetHost() {
    @Implementation
    protected fun startAppWidgetConfigureActivityForResult(activity: Activity, appWidgetId: Int, intentFlags: Int, requestCode: Int, options: Bundle?) {
        configured.add(appWidgetId)
    }

    companion object {
        val configured = mutableListOf<Int>()
    }
}
