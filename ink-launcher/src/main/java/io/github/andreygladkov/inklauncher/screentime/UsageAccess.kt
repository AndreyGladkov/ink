package io.github.andreygladkov.inklauncher.screentime

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings

class UsageAccess(private val context: Context) {
    fun isGranted(): Boolean {
        val mode = context.getSystemService(AppOpsManager::class.java)
            .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT -> context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    fun request() {
        val settings = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val forThisApp = Intent(settings).setData(Uri.fromParts("package", context.packageName, null))
        listOf(forThisApp, settings).firstOrNull(::opens)
    }

    private fun opens(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    fun log(since: Long, until: Long): UsageLog {
        val events = ArrayList<UsageEvent>()
        val source = context.getSystemService(UsageStatsManager::class.java).queryEvents(since, until) ?: return UsageLog(since, until, events)
        val event = UsageEvents.Event()
        while (source.getNextEvent(event)) {
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageKind.FOREGROUND
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageKind.BACKGROUND
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> UsageKind.SCREEN_OFF
                UsageEvents.Event.KEYGUARD_HIDDEN -> UsageKind.UNLOCK
                UsageEvents.Event.DEVICE_STARTUP -> UsageKind.STARTUP
                else -> continue
            }
            events += UsageEvent(event.timeStamp, kind, event.packageName)
        }
        return UsageLog(since, until, events)
    }
}
