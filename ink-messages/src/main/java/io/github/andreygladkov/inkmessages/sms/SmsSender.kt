package io.github.andreygladkov.inkmessages.sms

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony.Sms
import android.provider.Telephony.TextBasedSmsColumns
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

object SmsSender {
    fun send(context: Context, address: String, text: String, subId: Int): Uri? {
        val values = ContentValues().apply {
            put(Sms.ADDRESS, address)
            put(Sms.BODY, text)
            put(Sms.DATE, System.currentTimeMillis())
            put(Sms.READ, 1)
            put(Sms.SEEN, 1)
            put(Sms.TYPE, TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX)
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) put(Sms.SUBSCRIPTION_ID, subId)
        }
        val uri = context.contentResolver.insert(Sms.Outbox.CONTENT_URI, values) ?: return null
        transmit(context, uri, address, text, subId)
        return uri
    }

    fun retry(context: Context, uri: Uri, address: String, text: String, subId: Int) {
        context.contentResolver.update(uri, ContentValues().apply {
            put(Sms.TYPE, TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX)
            put(Sms.DATE, System.currentTimeMillis())
        }, null, null)
        transmit(context, uri, address, text, subId)
    }

    private fun transmit(context: Context, uri: Uri, address: String, text: String, subId: Int) {
        runCatching {
            val manager = smsManager(context, subId)
            val parts = manager.divideMessage(text)
            val sent = ArrayList(parts.indices.map { index -> sentIntent(context, uri, isLast = index == parts.lastIndex) })
            manager.sendMultipartTextMessage(address, null, parts, sent, null)
        }.onFailure { SmsSentReceiver.markFailed(context, uri, SmsManager.RESULT_ERROR_GENERIC_FAILURE) }
    }

    private fun sentIntent(context: Context, uri: Uri, isLast: Boolean): PendingIntent {
        val intent = Intent(context, SmsSentReceiver::class.java)
            .setData(uri)
            .putExtra(SmsSentReceiver.EXTRA_LAST_PART, isLast)
        val requestCode = (uri.lastPathSegment?.toIntOrNull() ?: 0) * 2 + if (isLast) 1 else 0
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    @Suppress("DEPRECATION")
    fun smsManager(context: Context, subId: Int): SmsManager {
        val valid = subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val base = context.getSystemService(SmsManager::class.java)
            if (valid) base.createForSubscriptionId(subId) else base
        } else {
            if (valid) SmsManager.getSmsManagerForSubscriptionId(subId) else SmsManager.getDefault()
        }
    }
}
