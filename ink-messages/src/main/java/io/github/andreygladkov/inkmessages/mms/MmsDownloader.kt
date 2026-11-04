package io.github.andreygladkov.inkmessages.mms

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import io.github.andreygladkov.inkmessages.sms.SmsSender
import java.util.concurrent.ConcurrentHashMap

object MmsDownloader {
    private val active: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    fun isActive(messageId: Long) = messageId in active

    fun start(context: Context, notification: MmsNotification, automatic: Boolean) {
        val app = context.applicationContext
        val id = ContentUris.parseId(notification.uri)
        if (!active.add(id)) return
        val store = MmsStore(app)
        val file = MmsFiles.file(app, "retrieve-$id.pdu")
        val result = Intent(app, MmsResultReceiver::class.java)
            .setAction(MmsResultReceiver.ACTION_DOWNLOADED)
            .setData(notification.uri)
            .putExtra(MmsResultReceiver.EXTRA_FILE, file.name)
            .putExtra(MmsResultReceiver.EXTRA_AUTOMATIC, automatic)
        // Mutable because the MMS service adds the HTTP status to this intent.
        val pending = PendingIntent.getBroadcast(app, id.toInt(), result, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        runCatching {
            store.clearFailure(notification.uri)
            SmsSender.smsManager(app, notification.subId).downloadMultimediaMessage(app, notification.location, MmsFiles.uri(app, file), null, pending)
        }.onFailure {
            finished(id)
            store.markFailed(notification.uri)
        }
    }

    fun finished(messageId: Long) {
        active.remove(messageId)
    }
}
