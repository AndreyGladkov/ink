package io.github.andreygladkov.inkmessages.data

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.BaseColumns
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import android.provider.Telephony.TextBasedSmsColumns

class TelephonyStore(private val resolver: ContentResolver) {
    fun threadAddresses(): Map<Long, List<String>> {
        val addresses = HashMap<Long, String>()
        resolver.query(CANONICAL_ADDRESSES, arrayOf(BaseColumns._ID, "address"), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) addresses[cursor.getLong(0)] = cursor.getString(1).orEmpty()
        }
        val threads = HashMap<Long, List<String>>()
        resolver.query(SIMPLE_THREADS, arrayOf(BaseColumns._ID, "recipient_ids"), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val ids = cursor.getString(1).orEmpty().split(' ').mapNotNull(String::toLongOrNull)
                threads[cursor.getLong(0)] = ids.mapNotNull(addresses::get).filter(String::isNotEmpty)
            }
        }
        return threads
    }

    /** Every message without its text, for finding the latest one and counting unread ones per thread. */
    fun headers(): List<Message> = smsHeaders() + mmsHeaders()

    fun withContent(headers: List<Message>): List<Message> {
        val smsBodies = smsBodies(headers.filter { !it.isMms }.map { it.id })
        val mmsContent = mmsContent(headers.filter { it.isMms }.map { it.id })
        return headers.map { message ->
            if (message.isMms) {
                val content = mmsContent[message.id]
                message.copy(text = content?.text.orEmpty(), attachments = content?.attachments.orEmpty())
            } else {
                message.copy(text = smsBodies[message.id].orEmpty())
            }
        }
    }

    fun thread(threadId: Long): List<Message> {
        val headers = smsHeaders("${Sms.THREAD_ID} = $threadId") + mmsHeaders("${Mms.THREAD_ID} = $threadId")
        return withContent(headers).sortedWith(compareBy(Message::date, Message::key))
    }

    private fun smsHeaders(extra: String? = null): List<Message> {
        val selection = listOfNotNull("${Sms.TYPE} != ${TextBasedSmsColumns.MESSAGE_TYPE_DRAFT}", extra).joinToString(" AND ")
        val projection = arrayOf(Sms._ID, Sms.THREAD_ID, Sms.DATE, Sms.TYPE, Sms.READ, Sms.SUBSCRIPTION_ID)
        return resolver.query(Sms.CONTENT_URI, projection, selection, null, null)?.use { cursor ->
            cursor.map {
                val type = it.getInt(3)
                val incoming = type == TextBasedSmsColumns.MESSAGE_TYPE_INBOX
                Message(
                    id = it.getLong(0), isMms = false, threadId = it.getLong(1), date = it.getLong(2), text = "",
                    isOutgoing = !incoming, isUnread = incoming && it.getInt(4) == 0, subId = it.getInt(5), status = smsStatus(type),
                )
            }
        }.orEmpty()
    }

    private fun mmsHeaders(extra: String? = null): List<Message> {
        val selection = listOfNotNull("${Mms.MESSAGE_BOX} != ${Mms.MESSAGE_BOX_DRAFTS}", extra).joinToString(" AND ")
        val projection = arrayOf(Mms._ID, Mms.THREAD_ID, Mms.DATE, Mms.MESSAGE_BOX, Mms.READ, Mms.SUBSCRIPTION_ID)
        return resolver.query(Mms.CONTENT_URI, projection, selection, null, null)?.use { cursor ->
            cursor.map {
                val box = it.getInt(3)
                val incoming = box == Mms.MESSAGE_BOX_INBOX
                Message(
                    id = it.getLong(0), isMms = true, threadId = it.getLong(1),
                    // The MMS table stores dates in seconds, unlike SMS.
                    date = it.getLong(2) * 1000, text = "",
                    isOutgoing = !incoming, isUnread = incoming && it.getInt(4) == 0, subId = it.getInt(5),
                    status = if (box == Mms.MESSAGE_BOX_OUTBOX) SendStatus.SENDING else SendStatus.NONE,
                )
            }
        }.orEmpty()
    }

    private fun smsStatus(type: Int) = when (type) {
        TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX, TextBasedSmsColumns.MESSAGE_TYPE_QUEUED -> SendStatus.SENDING
        TextBasedSmsColumns.MESSAGE_TYPE_FAILED -> SendStatus.FAILED
        else -> SendStatus.NONE
    }

    private fun smsBodies(ids: List<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        return resolver.query(Sms.CONTENT_URI, arrayOf(Sms._ID, Sms.BODY), "${Sms._ID} IN (${ids.joinToString(",")})", null, null)?.use { cursor ->
            cursor.map { it.getLong(0) to it.getString(1).orEmpty() }.toMap()
        }.orEmpty()
    }

    private fun mmsContent(ids: List<Long>): Map<Long, MmsContent> {
        if (ids.isEmpty()) return emptyMap()
        val texts = HashMap<Long, MutableList<String>>()
        val attachments = HashMap<Long, MutableList<Attachment>>()
        resolver.query(MMS_PARTS, arrayOf(Mms.Part.MSG_ID, Mms.Part.CONTENT_TYPE, Mms.Part.TEXT), "${Mms.Part.MSG_ID} IN (${ids.joinToString(",")})", null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val type = cursor.getString(1).orEmpty().lowercase()
                when {
                    type == "text/plain" -> cursor.getString(2)?.takeIf(String::isNotEmpty)?.let { texts.getOrPut(id, ::ArrayList) += it }
                    type == "application/smil" -> Unit
                    type.startsWith("image/") -> attachments.getOrPut(id, ::ArrayList) += Attachment.IMAGE
                    else -> attachments.getOrPut(id, ::ArrayList) += Attachment.OTHER
                }
            }
        }
        return ids.associateWith { MmsContent(texts[it].orEmpty().joinToString("\n"), attachments[it].orEmpty()) }
    }

    private data class MmsContent(val text: String, val attachments: List<Attachment>)

    private fun <T> Cursor.map(row: (Cursor) -> T): List<T> = buildList { while (moveToNext()) add(row(this@map)) }

    companion object {
        val SIMPLE_THREADS: Uri = Uri.parse("content://mms-sms/conversations?simple=true")
        val CANONICAL_ADDRESSES: Uri = Uri.parse("content://mms-sms/canonical-addresses")
        val MMS_PARTS: Uri = Uri.parse("content://mms/part")
        val CHANGES: Uri = Uri.parse("content://mms-sms/")
    }
}
