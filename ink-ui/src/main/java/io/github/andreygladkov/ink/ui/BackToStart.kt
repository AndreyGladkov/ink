package io.github.andreygladkov.ink.ui

import android.app.Activity
import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/**
 * Takes the back press while [isAway]: a main screen first returns to the tab it opened on, then closes.
 * API 33+ delivers back only to a registered callback; Android 11–12 only to onBackPressed, which the
 * activity forwards to [onBackPressed].
 */
class BackToStart(private val activity: Activity, private val returnToStart: () -> Unit) {
    private val callback: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) OnBackInvokedCallback { returnToStart() } else null

    var isAway = false
        set(value) {
            if (value == field) return
            field = value
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            val dispatcher = activity.onBackInvokedDispatcher
            val callback = callback as OnBackInvokedCallback
            if (value) dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            else dispatcher.unregisterOnBackInvokedCallback(callback)
        }

    /** True when the press was used to return to the start. */
    fun onBackPressed(): Boolean {
        if (!isAway) return false
        returnToStart()
        return true
    }
}
