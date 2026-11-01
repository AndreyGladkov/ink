package io.github.andreygladkov.inkphone.call

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager

class MissedCallsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        CallNotifications.createChannels(context)
        val pending = goAsync()
        Thread {
            try {
                @Suppress("DEPRECATION")
                CallNotifications.showMissed(
                    context,
                    count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0),
                    number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER),
                    callBack = intent.getParcelableExtra(EXTRA_CALL_BACK_INTENT) as? PendingIntent,
                )
            } finally {
                pending.finish()
            }
        }.start()
    }

    private companion object {
        // Sent by Telecom with ACTION_SHOW_MISSED_CALLS_NOTIFICATION but not exposed as a public constant in the SDK.
        const val EXTRA_CALL_BACK_INTENT = "android.telecom.extra.CALL_BACK_INTENT"
    }
}
