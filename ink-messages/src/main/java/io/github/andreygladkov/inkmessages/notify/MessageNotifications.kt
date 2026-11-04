package io.github.andreygladkov.inkmessages.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import io.github.andreygladkov.inkmessages.Category
import io.github.andreygladkov.inkmessages.Labels
import io.github.andreygladkov.inkmessages.MainActivity
import io.github.andreygladkov.inkmessages.R
import io.github.andreygladkov.inkmessages.contacts.ContactDirectory
import io.github.andreygladkov.inkmessages.contacts.Participant
import io.github.andreygladkov.inkmessages.data.Loader
import io.github.andreygladkov.inkmessages.data.Message
import io.github.andreygladkov.inkmessages.data.TelephonyStore
import io.github.andreygladkov.inkmessages.sms.OtpCode
import io.github.andreygladkov.inkmessages.sms.ThreadState
import io.github.andreygladkov.inkmessages.thread.ThreadActivity

object MessageNotifications {
    private const val MAX_MESSAGES = 8

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(listOf(Category.FAVORITES, Category.CONTACTS, Category.OTHER).map { category ->
            NotificationChannel(channelId(category), context.getString(category.title), NotificationManager.IMPORTANCE_HIGH)
        })
    }

    fun channelId(category: Category) = "messages_${category.name.lowercase()}"

    fun categoryOf(participants: List<Participant>): Category = when {
        participants.any { it.isStarred } -> Category.FAVORITES
        participants.any { it.isContact } -> Category.CONTACTS
        else -> Category.OTHER
    }

    fun onReceived(context: Context, threadId: Long, done: () -> Unit) {
        val app = context.applicationContext
        Loader.executor.execute {
            try {
                if (ThreadState.visibleThreadId != threadId) showUnread(app, threadId)
            } finally {
                done()
            }
        }
    }

    fun onReplied(context: Context, threadId: Long, since: Long, done: () -> Unit) {
        val app = context.applicationContext
        Loader.executor.execute {
            try {
                ThreadState.updateRead(app, threadId)
                val messages = TelephonyStore(app.contentResolver).thread(threadId).filter { it.date >= since }.takeLast(MAX_MESSAGES)
                if (messages.isNotEmpty()) show(app, threadId, messages, since, alert = false)
            } finally {
                done()
            }
        }
    }

    fun cancel(context: Context, threadId: Long) = NotificationManagerCompat.from(context).cancel(threadId.toInt())

    private fun showUnread(context: Context, threadId: Long) {
        val unread = TelephonyStore(context.contentResolver).thread(threadId).filter { it.isUnread }.takeLast(MAX_MESSAGES)
        if (unread.isNotEmpty()) show(context, threadId, unread, since = unread.first().date, alert = true)
    }

    private fun show(context: Context, threadId: Long, messages: List<Message>, since: Long, alert: Boolean) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val store = TelephonyStore(context.contentResolver)
        val contacts = ContactDirectory(context.contentResolver)
        val participants = store.threadAddresses()[threadId].orEmpty().map(contacts::participant)
        val labels = Labels(context)
        val title = labels.title(participants)
        val sender = Person.Builder().setName(title).build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName(context.getString(R.string.me)).build())
            .setConversationTitle(if (participants.size > 1) title else null)
            .setGroupConversation(participants.size > 1)
        messages.forEach { style.addMessage(body(labels, it), it.date, if (it.isOutgoing) null else sender) }
        val builder = NotificationCompat.Builder(context, channelId(categoryOf(participants)))
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setWhen(messages.last().date)
            .setAutoCancel(true)
            .setOnlyAlertOnce(!alert)
            .setContentIntent(openThread(context, threadId))
        val single = participants.singleOrNull()
        if (single != null && ContactDirectory.isPhoneNumber(single.address)) builder.addAction(replyAction(context, threadId, single.address, since))
        builder.addAction(0, context.getString(R.string.action_mark_read), NotificationActions.read(context, threadId))
        messages.lastOrNull { !it.isOutgoing }?.let { OtpCode.find(it.text) }?.let { code ->
            builder.addAction(0, context.getString(R.string.action_copy_code, code), NotificationActions.copy(context, threadId, code))
        }
        NotificationManagerCompat.from(context).notify(threadId.toInt(), builder.build())
    }

    private fun body(labels: Labels, message: Message) =
        (labels.attachments(message) + listOf(message.text).filter(String::isNotEmpty)).joinToString("\n")

    private fun replyAction(context: Context, threadId: Long, address: String, since: Long): NotificationCompat.Action {
        val input = RemoteInput.Builder(NotificationActions.KEY_REPLY).setLabel(context.getString(R.string.action_reply)).build()
        return NotificationCompat.Action.Builder(0, context.getString(R.string.action_reply), NotificationActions.reply(context, threadId, address, since))
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }

    private fun openThread(context: Context, threadId: Long): PendingIntent =
        TaskStackBuilder.create(context)
            .addNextIntent(Intent(context, MainActivity::class.java))
            .addNextIntent(ThreadActivity.intent(context, threadId))
            .getPendingIntent(threadId.toInt(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)!!
}
