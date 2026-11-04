package io.github.andreygladkov.inkphone.call

import android.content.Context
import io.github.andreygladkov.ink.eink.A9Panel
import io.github.andreygladkov.ink.inkPrefs

object CallPanelClear {
    const val PREF_CLEAR_AFTER_CALL = "a9_clear_after_call"

    fun onCallScreenClosed(context: Context) {
        if (context.inkPrefs().getBoolean(PREF_CLEAR_AFTER_CALL, false)) A9Panel.clearSoon(context)
    }
}
