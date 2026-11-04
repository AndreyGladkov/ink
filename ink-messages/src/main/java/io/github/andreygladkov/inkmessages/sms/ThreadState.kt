package io.github.andreygladkov.inkmessages.sms

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import io.github.andreygladkov.inkmessages.data.Loader
import io.github.andreygladkov.inkmessages.notify.MessageNotifications

object ThreadState {
    @Volatile
    var visibleThreadId: Long? = null

    fun markRead(context: Context, threadId: Long) {
        val app = context.applicationContext
        MessageNotifications.cancel(app, threadId)
        Loader.executor.execute {
            val read = ContentValues().apply {
                put(Sms.READ, 1)
                put(Sms.SEEN, 1)
            }
            runCatching { app.contentResolver.update(Sms.CONTENT_URI, read, "${Sms.THREAD_ID} = $threadId AND ${Sms.READ} = 0", null) }
            runCatching { app.contentResolver.update(Mms.CONTENT_URI, read, "${Mms.THREAD_ID} = $threadId AND ${Mms.READ} = 0", null) }
        }
    }
}
