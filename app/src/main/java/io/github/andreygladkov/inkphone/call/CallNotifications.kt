package io.github.andreygladkov.inkphone.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.andreygladkov.inkphone.MainActivity
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.Tab

object CallNotifications {
    private const val CHANNEL_INCOMING = "incoming"
    private const val CHANNEL_ONGOING = "ongoing"
    private const val CHANNEL_MISSED = "missed"
    private const val ID_CALL = 1
    private const val ID_MISSED = 2
    private const val ANSWER_REQUEST = 3
    private const val ACTION_DECLINE = "io.github.andreygladkov.inkphone.DECLINE"
    private const val ACTION_HANG_UP = "io.github.andreygladkov.inkphone.HANG_UP"
    private const val ACTION_SPEAKER = "io.github.andreygladkov.inkphone.SPEAKER"
    private const val EXTRA_CALL_ID = "call_id"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(
            // Telecom plays the ringtone itself, a sound on the channel would ring twice.
            NotificationChannel(CHANNEL_INCOMING, context.getString(R.string.notification_channel_incoming), NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(null, null) },
            NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.notification_channel_ongoing), NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_MISSED, context.getString(R.string.notification_channel_missed), NotificationManager.IMPORTANCE_DEFAULT),
        ))
    }

    fun update(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val unknown = context.getString(R.string.call_unknown)
        val screen = PendingIntent.getActivity(context, 0, CallActivity.intent(context), PendingIntent.FLAG_IMMUTABLE)
        val notification = when (val model = CallManager.model()) {
            is CallScreenModel.Incoming, is CallScreenModel.Waiting -> {
                val call = (model as? CallScreenModel.Incoming)?.call ?: (model as CallScreenModel.Waiting).incoming
                Notification.Builder(context, CHANNEL_INCOMING)
                    .setSmallIcon(R.drawable.ic_call)
                    .setContentTitle(call.title ?: unknown)
                    .setContentText(context.getString(R.string.call_incoming))
                    .setCategory(Notification.CATEGORY_CALL)
                    .setOngoing(true)
                    .setContentIntent(screen)
                    .setFullScreenIntent(screen, true)
                    .addAction(action(context, ACTION_DECLINE, call.id, R.string.call_decline))
                    .addAction(Notification.Action.Builder(null, context.getString(R.string.call_answer), answerIntent(context, call.id)).build())
                    .build()
            }
            is CallScreenModel.InCall -> Notification.Builder(context, CHANNEL_ONGOING)
                .setSmallIcon(R.drawable.ic_call)
                .setContentTitle(model.primary.title ?: unknown)
                .setContentText(context.getString(R.string.call_active))
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setUsesChronometer(false)
                .setContentIntent(screen)
                .addAction(action(context, ACTION_SPEAKER, model.primary.id, R.string.call_speaker))
                .addAction(action(context, ACTION_HANG_UP, model.primary.id, R.string.call_end))
                .build()
            else -> null
        }
        if (notification == null) manager.cancel(ID_CALL) else manager.notify(ID_CALL, notification)
    }

    fun showMissed(context: Context, count: Int, number: String?, callBack: PendingIntent?) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (count <= 0) {
            manager.cancel(ID_MISSED)
            return
        }
        val log = PendingIntent.getActivity(
            context, 1, MainActivity.intent(context, Tab.CALL_LOG), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val name = number?.let { ContactLookup.find(context, it)?.name ?: it }
        val builder = Notification.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(if (count == 1) context.getString(R.string.missed_call) else context.getString(R.string.missed_calls, count))
            .setContentText(if (count == 1) name else null)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setContentIntent(log)
            .setAutoCancel(true)
        if (count == 1 && callBack != null) {
            builder.addAction(Notification.Action.Builder(null, context.getString(R.string.call_back), callBack).build())
        }
        manager.notify(ID_MISSED, builder.build())
    }

    // Android 12+ blocks starting an activity from a broadcast sent by a notification action,
    // so answering opens the call screen directly and lets it answer.
    fun answerIntent(context: Context, callId: String): PendingIntent = PendingIntent.getActivity(
        context, ANSWER_REQUEST, CallActivity.answerIntent(context, callId), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, action: String, callId: String, title: Int): Notification.Action {
        val intent = Intent(action).setClass(context, ActionReceiver::class.java).putExtra(EXTRA_CALL_ID, callId)
        val pending = PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(null, context.getString(title), pending).build()
    }

    class ActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getStringExtra(EXTRA_CALL_ID) ?: return
            when (intent.action) {
                ACTION_DECLINE -> CallManager.reject(id)
                ACTION_HANG_UP -> CallManager.hangUp(id)
                ACTION_SPEAKER -> {
                    val speaker = CallManager.audio().route == AudioRoute.SPEAKER
                    CallManager.setRoute(if (speaker) AudioRoute.EARPIECE else AudioRoute.SPEAKER)
                }
            }
        }
    }
}
