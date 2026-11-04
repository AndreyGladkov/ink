package io.github.andreygladkov.inkmessages.mms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import io.github.andreygladkov.inkmessages.data.Loader
import io.github.andreygladkov.inkmessages.mmslib.pdu.NotificationInd
import io.github.andreygladkov.inkmessages.mmslib.pdu.PduParser

class MmsPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION || intent.type != MMS_TYPE) return
        val data = intent.getByteArrayExtra(EXTRA_DATA) ?: return
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        val app = context.applicationContext
        val pending = goAsync()
        Loader.executor.execute {
            try {
                val ind = PduParser(data, true).parse() as? NotificationInd ?: return@execute
                val notification = MmsStore(app).saveNotification(ind, subId) ?: return@execute
                MmsDownloader.start(app, notification, automatic = true)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val MMS_TYPE = "application/vnd.wap.mms-message"
        const val EXTRA_DATA = "data"
    }
}
