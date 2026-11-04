package io.github.andreygladkov.inkmessages.mms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import io.github.andreygladkov.inkmessages.data.Loader
import io.github.andreygladkov.inkmessages.mmslib.pdu.NotifyRespInd
import io.github.andreygladkov.inkmessages.mmslib.pdu.PduComposer
import io.github.andreygladkov.inkmessages.mmslib.pdu.PduHeaders
import io.github.andreygladkov.inkmessages.mmslib.pdu.PduParser
import io.github.andreygladkov.inkmessages.mmslib.pdu.RetrieveConf
import io.github.andreygladkov.inkmessages.notify.MessageNotifications
import io.github.andreygladkov.inkmessages.sms.SmsSender

class MmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val file = intent.getStringExtra(EXTRA_FILE)?.let { MmsFiles.file(app, it) }
        when (intent.action) {
            ACTION_RESPONSE_SENT -> file?.delete()
            ACTION_DOWNLOADED -> {
                val uri = intent.data ?: return
                val succeeded = resultCode == Activity.RESULT_OK
                val automatic = intent.getBooleanExtra(EXTRA_AUTOMATIC, false)
                val pending = goAsync()
                Loader.executor.execute {
                    val threadToNotify = try {
                        val bytes = file?.takeIf { succeeded && it.exists() }?.readBytes()
                        downloaded(app, ContentUris.parseId(uri), bytes, automatic)
                    } finally {
                        file?.delete()
                        MmsDownloader.finished(ContentUris.parseId(uri))
                    }
                    if (threadToNotify == null) pending.finish() else MessageNotifications.onReceived(app, threadToNotify) { pending.finish() }
                }
            }
        }
    }

    companion object {
        const val ACTION_DOWNLOADED = "io.github.andreygladkov.inkmessages.MMS_DOWNLOADED"
        const val ACTION_RESPONSE_SENT = "io.github.andreygladkov.inkmessages.MMS_RESPONSE_SENT"
        const val EXTRA_FILE = "file"
        const val EXTRA_AUTOMATIC = "automatic"

        /** Stores a finished download; returns the thread to notify about, if any. */
        fun downloaded(context: Context, messageId: Long, pdu: ByteArray?, automatic: Boolean): Long? {
            val store = MmsStore(context)
            val notification = store.notification(messageId) ?: return null
            val conf = pdu?.let { PduParser(it, true).parse() } as? RetrieveConf
            if (conf == null || conf.retrieveStatus !in OK_STATUSES) {
                store.markFailed(notification.uri)
                // Only the first failure is announced; a failed manual retry is already in front of the user.
                return if (automatic) store.threadIdOf(notification.uri) else null
            }
            val message = store.saveRetrieved(conf, notification, selfNumbers(context, notification.subId)) ?: return null
            sendRetrievedResponse(context, notification)
            return store.threadIdOf(message)
        }

        /** Tells the carrier the message arrived, so it stops pushing the same notification again. */
        private fun sendRetrievedResponse(context: Context, notification: MmsNotification) {
            runCatching {
                val response = NotifyRespInd(PduHeaders.CURRENT_MMS_VERSION, notification.transactionId.toByteArray(Charsets.ISO_8859_1), PduHeaders.STATUS_RETRIEVED)
                val bytes = PduComposer(context, response).make() ?: return
                val file = MmsFiles.file(context, "notifyresp-${ContentUris.parseId(notification.uri)}.pdu").apply { writeBytes(bytes) }
                val sent = Intent(context, MmsResultReceiver::class.java).setAction(ACTION_RESPONSE_SENT).putExtra(EXTRA_FILE, file.name)
                val pending = PendingIntent.getBroadcast(context, file.name.hashCode(), sent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                SmsSender.smsManager(context, notification.subId).sendMultimediaMessage(context, MmsFiles.uri(context, file), null, null, pending)
            }
        }

        private fun selfNumbers(context: Context, subId: Int): List<String> {
            if (context.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) return emptyList()
            val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
            val number = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) manager.getPhoneNumber(subId)
                else @Suppress("DEPRECATION") manager.getActiveSubscriptionInfo(subId)?.number
            }.getOrNull()
            return listOfNotNull(number).filter(String::isNotBlank)
        }

        private val OK_STATUSES = setOf(0, PduHeaders.RETRIEVE_STATUS_OK)
    }
}
