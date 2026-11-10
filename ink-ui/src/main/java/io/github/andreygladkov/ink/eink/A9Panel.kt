package io.github.andreygladkov.ink.eink

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.andreygladkov.ink.inkPrefs

/** Full panel refreshes and refresh modes through A9PatchingService, which acts only while its "Allow Control via Broadcasts" is on. */
object A9Panel {
    /** The service's own names for the modes it accepts in a broadcast. */
    enum class RefreshMode { CLEAR, BALANCED, SMOOTH, SPEED }

    const val PREF_CLEAR_EVERY_PAGES = "a9_clear_every_pages"
    val PAGE_INTERVALS = listOf(0, 1, 3, 5, 10)

    private const val SERVICE_PACKAGE = "com.lmqr.ha9_comp_service"
    private const val ACTION_FORCE_CLEAR = "EINK_FORCE_CLEAR"
    private const val ACTION_TEMPORARY_MODE = "EINK_REFRESH_TEMPORARY"
    private const val ACTION_RESTORE_MODE = "EINK_REFRESH_RESTORE"
    private const val EXTRA_MODE = "mode"

    // The panel keeps working through queued updates after the frame is drawn; clearing earlier leaves ghosts.
    // Ink Keyboard found 400 ms too short and 800 ms enough after its window disappears.
    private const val CLEAR_DELAY_MS = 800L

    private val main = Handler(Looper.getMainLooper())
    private var flips = 0

    fun isInstalled(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(SERVICE_PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(SERVICE_PACKAGE, 0)
        }
    }.isSuccess

    fun onPageFlipped(context: Context) {
        val every = context.inkPrefs().getInt(PREF_CLEAR_EVERY_PAGES, 0)
        if (every <= 0) return
        flips++
        if (flips % every == 0) clearSoon(context)
    }

    fun clearSoon(context: Context) {
        val app = context.applicationContext
        main.postDelayed({ send(app, Intent(ACTION_FORCE_CLEAR)) }, CLEAR_DELAY_MS)
    }

    /**
     * Switches the panel to [mode] until [restoreMode] or another held mode. The service keeps this apart from the
     * mode it stores for each app, so it holds across app switches and is never saved as this app's mode.
     */
    fun holdMode(context: Context, mode: RefreshMode) = send(context, Intent(ACTION_TEMPORARY_MODE).putExtra(EXTRA_MODE, mode.name))

    fun restoreMode(context: Context) = send(context, Intent(ACTION_RESTORE_MODE))

    private fun send(context: Context, intent: Intent) =
        context.sendBroadcast(intent.setPackage(SERVICE_PACKAGE).addFlags(Intent.FLAG_RECEIVER_FOREGROUND))
}
