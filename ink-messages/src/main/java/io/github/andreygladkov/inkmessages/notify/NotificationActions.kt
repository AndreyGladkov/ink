package io.github.andreygladkov.inkmessages.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.inkmessages.sms.Sims
import io.github.andreygladkov.inkmessages.sms.SmsSender
import io.github.andreygladkov.inkmessages.sms.ThreadState

class NotificationActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1)
        if (threadId < 0) return
        when (intent.action) {
            ACTION_READ -> ThreadState.markRead(context, threadId)
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.takeIf(String::isNotBlank) ?: return
                val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
                val sims = Sims(context)
                val subId = sims.forThread(context.inkPrefs(), threadId, sims.active())?.subId ?: sims.defaultSubId()
                SmsSender.send(context, address, text, subId)
                val pending = goAsync()
                MessageNotifications.onReplied(context, threadId, intent.getLongExtra(EXTRA_SINCE, 0), pending::finish)
            }
            ACTION_COPY -> {
                val code = intent.getStringExtra(EXTRA_CODE) ?: return
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(code, code))
            }
        }
    }

    companion object {
        const val KEY_REPLY = "reply"
        private const val ACTION_READ = "io.github.andreygladkov.inkmessages.READ"
        private const val ACTION_REPLY = "io.github.andreygladkov.inkmessages.REPLY"
        private const val ACTION_COPY = "io.github.andreygladkov.inkmessages.COPY_CODE"
        private const val EXTRA_THREAD_ID = "thread_id"
        private const val EXTRA_ADDRESS = "address"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_SINCE = "since"

        fun read(context: Context, threadId: Long) = pending(context, ACTION_READ, threadId, PendingIntent.FLAG_IMMUTABLE)

        fun reply(context: Context, threadId: Long, address: String, since: Long) =
            pending(context, ACTION_REPLY, threadId, PendingIntent.FLAG_MUTABLE) {
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_SINCE, since)
            }

        fun copy(context: Context, threadId: Long, code: String) =
            pending(context, ACTION_COPY, threadId, PendingIntent.FLAG_IMMUTABLE) { putExtra(EXTRA_CODE, code) }

        private fun pending(context: Context, action: String, threadId: Long, mutability: Int, extras: Intent.() -> Unit = {}): PendingIntent {
            val intent = Intent(context, NotificationActions::class.java).setAction(action).putExtra(EXTRA_THREAD_ID, threadId).apply(extras)
            return PendingIntent.getBroadcast(context, threadId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or mutability)
        }
    }
}
