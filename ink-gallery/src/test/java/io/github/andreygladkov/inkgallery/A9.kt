package io.github.andreygladkov.inkgallery

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import org.robolectric.Shadows.shadowOf

object A9 {
    private const val SERVICE = "com.lmqr.ha9_comp_service"

    fun installService(context: Context) {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = SERVICE })
    }

    /** The refresh mode broadcasts sent to the A9 service, as action and mode. */
    fun modeChanges(context: Context) = shadowOf(context.applicationContext as Application).broadcastIntents
        .filter { it.`package` == SERVICE && it.action.orEmpty().startsWith("EINK_REFRESH") }
        .map { it.action.orEmpty().removePrefix("EINK_REFRESH_") + (it.getStringExtra("mode")?.let { mode -> " $mode" } ?: "") }
}
