package io.github.andreygladkov.inklauncher.notify

import android.app.ActivityOptions
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings

/** What the listener service last saw; the home screen reads it and is told of changes, all on the main thread. */
object NotificationFeed {
    private val observers = LinkedHashSet<() -> Unit>()
    private var listener: InkNotificationListener? = null

    var notifications: List<InkNotification> = emptyList()
        private set

    fun observe(observer: () -> Unit) {
        observers += observer
    }

    fun stopObserving(observer: () -> Unit) {
        observers -= observer
    }

    fun isAccessGranted(context: Context) = context.getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(ComponentName(context, InkNotificationListener::class.java))

    fun requestAccess(context: Context) {
        val component = ComponentName(context, InkNotificationListener::class.java).flattenToString()
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    fun open(context: Context, notification: InkNotification) {
        runCatching { notification.contentIntent?.send(context, 0, null, null, null, null, sendOptions()) }
        if (notification.isAutoCancel) dismiss(notification)
    }

    // Since Android 14 a pending intent sent by the visible launcher may start the app's activity only when the
    // launcher lends it its own right to start activities.
    private fun sendOptions(): Bundle? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS).toBundle()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> @Suppress("DEPRECATION") ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()
        else -> null
    }

    fun dismiss(notification: InkNotification) {
        listener?.cancelNotification(notification.key)
    }

    fun dismissAll() {
        listener?.cancelAllNotifications()
    }

    internal fun connect(service: InkNotificationListener) {
        listener = service
    }

    internal fun disconnect(service: InkNotificationListener) {
        if (listener === service) listener = null
        publish(emptyList())
    }

    fun publish(notifications: List<InkNotification>) {
        if (notifications == this.notifications) return
        this.notifications = notifications
        observers.toList().forEach { it() }
    }
}
