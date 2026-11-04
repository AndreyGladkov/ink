package io.github.andreygladkov.ink.eink

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.andreygladkov.ink.inkPrefs

/** Full panel refreshes through A9PatchingService, which acts only while its "Allow Control via Broadcasts" is on. */
object A9Panel {
    const val PREF_CLEAR_EVERY_PAGES = "a9_clear_every_pages"
    val PAGE_INTERVALS = listOf(0, 1, 3, 5, 10)

    private const val SERVICE_PACKAGE = "com.lmqr.ha9_comp_service"
    private const val ACTION_FORCE_CLEAR = "EINK_FORCE_CLEAR"

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
        main.postDelayed({
            app.sendBroadcast(Intent(ACTION_FORCE_CLEAR).setPackage(SERVICE_PACKAGE).addFlags(Intent.FLAG_RECEIVER_FOREGROUND))
        }, CLEAR_DELAY_MS)
    }
}
