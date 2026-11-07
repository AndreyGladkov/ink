package io.github.andreygladkov.inklauncher.apps

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import androidx.core.net.toUri
import io.github.andreygladkov.inklauncher.home.AppKey
import java.text.Collator

data class LauncherApp(val key: AppKey, val label: String)

class AppCatalog(private val context: Context) {
    private val pm = context.packageManager
    private var cached: List<LauncherApp>? = null

    fun apps(): List<LauncherApp> = cached ?: load().also { cached = it }

    fun invalidate() {
        cached = null
    }

    fun label(key: AppKey): String = apps().firstOrNull { it.key == key }?.label ?: key.packageName

    fun appLabel(packageName: String): String = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    fun icon(key: AppKey): Drawable? = runCatching { pm.getActivityIcon(ComponentName(key.packageName, key.className)) }.getOrNull()

    fun version(packageName: String): Long = runCatching { pm.getPackageInfo(packageName, 0).lastUpdateTime }.getOrDefault(0L)

    fun launch(key: AppKey): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(key.packageName, key.className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return start(intent)
    }

    fun openDetails(packageName: String) =
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun uninstall(packageName: String) =
        start(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** The phone, messages, browser and camera the system would open, for the first dock. */
    fun defaultDock(): List<AppKey> {
        val packages = listOfNotNull(
            handler(Intent(Intent.ACTION_DIAL)),
            Telephony.Sms.getDefaultSmsPackage(context),
            handler(Intent(Intent.ACTION_VIEW, "https://example.com".toUri())),
            handler(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        )
        return packages.distinct().mapNotNull { pkg -> apps().firstOrNull { it.key.packageName == pkg }?.key }
    }

    private fun handler(intent: Intent): String? =
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.takeIf { it != SYSTEM_RESOLVER }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent, ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle())
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun load(): List<LauncherApp> {
        val collator = Collator.getInstance(context.resources.configuration.locales[0])
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .map { LauncherApp(AppKey(it.packageName, it.name), it.loadLabel(pm).toString().trim()) }
            .distinctBy { it.key }
            .sortedWith(compareBy(collator) { it.label })
    }

    private companion object {
        const val SYSTEM_RESOLVER = "android"
    }
}
