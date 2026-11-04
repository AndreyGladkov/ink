package io.github.andreygladkov.inkmessages.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony.Sms
import android.provider.Telephony.TextBasedSmsColumns

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        if (resultCode != Activity.RESULT_OK) {
            markFailed(context, uri, resultCode)
        } else if (intent.getBooleanExtra(EXTRA_LAST_PART, true)) {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(Sms.TYPE, TextBasedSmsColumns.MESSAGE_TYPE_SENT) },
                "${Sms.TYPE} = ${TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX}",
                null,
            )
        }
    }

    companion object {
        const val EXTRA_LAST_PART = "last_part"

        fun markFailed(context: Context, uri: Uri, errorCode: Int) {
            context.contentResolver.update(uri, ContentValues().apply {
                put(Sms.TYPE, TextBasedSmsColumns.MESSAGE_TYPE_FAILED)
                put(Sms.ERROR_CODE, errorCode)
            }, null, null)
        }
    }
}
