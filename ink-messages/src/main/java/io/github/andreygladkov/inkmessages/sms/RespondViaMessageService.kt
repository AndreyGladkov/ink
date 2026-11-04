package io.github.andreygladkov.inkmessages.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/** Sends the quick reply a dialer chose when rejecting a call. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.data?.schemeSpecificPart?.substringBefore('?')?.takeIf(String::isNotBlank)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.takeIf(String::isNotBlank)
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE && address != null && text != null) {
            val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, Sims(this).defaultSubId())
            SmsSender.send(this, address, text, subId)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
