package io.github.andreygladkov.inkmessages.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony.Sms
import android.provider.Telephony.TextBasedSmsColumns
import android.telephony.SubscriptionManager
import io.github.andreygladkov.inkmessages.notify.MessageNotifications

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        val first = parts.firstOrNull() ?: return
        val address = first.displayOriginatingAddress ?: first.originatingAddress ?: return
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        val values = ContentValues().apply {
            put(Sms.ADDRESS, address)
            put(Sms.BODY, parts.joinToString("") { it.displayMessageBody ?: it.messageBody.orEmpty() })
            put(Sms.DATE, System.currentTimeMillis())
            put(Sms.DATE_SENT, first.timestampMillis)
            put(Sms.READ, 0)
            put(Sms.SEEN, 0)
            put(Sms.TYPE, TextBasedSmsColumns.MESSAGE_TYPE_INBOX)
            put(Sms.PROTOCOL, first.protocolIdentifier)
            put(Sms.SERVICE_CENTER, first.serviceCenterAddress)
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) put(Sms.SUBSCRIPTION_ID, subId)
        }
        val uri = context.contentResolver.insert(Sms.Inbox.CONTENT_URI, values) ?: return
        val threadId = context.contentResolver.query(uri, arrayOf(Sms.THREAD_ID), null, null, null)?.use {
            if (it.moveToFirst()) it.getLong(0) else null
        } ?: return
        val pending = goAsync()
        MessageNotifications.onReceived(context, threadId) { pending.finish() }
    }
}
