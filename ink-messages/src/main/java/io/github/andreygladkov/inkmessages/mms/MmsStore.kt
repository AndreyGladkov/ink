package io.github.andreygladkov.inkmessages.mms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony.Mms
import android.provider.Telephony.Threads
import io.github.andreygladkov.inkmessages.mmslib.pdu.CharacterSets
import io.github.andreygladkov.inkmessages.mmslib.pdu.EncodedStringValue
import io.github.andreygladkov.inkmessages.mmslib.pdu.NotificationInd
import io.github.andreygladkov.inkmessages.mmslib.pdu.PduHeaders
import io.github.andreygladkov.inkmessages.mmslib.pdu.PduPart
import io.github.andreygladkov.inkmessages.mmslib.pdu.RetrieveConf

data class MmsNotification(val uri: Uri, val location: String, val transactionId: String, val subId: Int)

/** Writes received MMS into the telephony provider, which only the default SMS app may do. */
class MmsStore(private val context: Context) {
    private val resolver = context.contentResolver

    /** Stores the carrier's announcement as a placeholder; null when it repeats an earlier one or is unusable. */
    fun saveNotification(ind: NotificationInd, subId: Int, now: Long = System.currentTimeMillis()): MmsNotification? {
        val transactionId = ind.transactionId?.let(::ascii) ?: return null
        val location = ind.contentLocation?.let(::ascii) ?: return null
        if (isKnown(transactionId)) return null
        val from = ind.from?.string?.let(::address) ?: PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR
        val values = ContentValues().apply {
            put(Mms.THREAD_ID, Threads.getOrCreateThreadId(context, from))
            put(Mms.DATE, now / 1000)
            put(Mms.READ, 0)
            put(Mms.SEEN, 0)
            put(Mms.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND)
            put(Mms.MMS_VERSION, ind.mmsVersion)
            put(Mms.TRANSACTION_ID, transactionId)
            put(Mms.CONTENT_LOCATION, location)
            put(Mms.EXPIRY, ind.expiry)
            put(Mms.MESSAGE_SIZE, ind.messageSize)
            putSubject(ind.subject)
            put(Mms.SUBSCRIPTION_ID, subId)
        }
        val inserted = resolver.insert(Mms.Inbox.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(inserted)
        addAddress(id, from, PduHeaders.FROM)
        return MmsNotification(ContentUris.withAppendedId(Mms.CONTENT_URI, id), location, transactionId, subId)
    }

    fun notification(messageId: Long): MmsNotification? {
        val uri = ContentUris.withAppendedId(Mms.CONTENT_URI, messageId)
        val projection = arrayOf(Mms.MESSAGE_TYPE, Mms.CONTENT_LOCATION, Mms.TRANSACTION_ID, Mms.SUBSCRIPTION_ID)
        return resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst() || cursor.getInt(0) != PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) return null
            val location = cursor.getString(1) ?: return null
            MmsNotification(uri, location, cursor.getString(2).orEmpty(), cursor.getInt(3))
        }
    }

    fun markFailed(notification: Uri, status: Int = PduHeaders.RETRIEVE_STATUS_ERROR_TRANSIENT_FAILURE) {
        resolver.update(notification, ContentValues().apply { put(Mms.RETRIEVE_STATUS, status) }, null, null)
    }

    fun clearFailure(notification: Uri) {
        resolver.update(notification, ContentValues().apply { putNull(Mms.RETRIEVE_STATUS) }, null, null)
    }

    /**
     * Replaces the placeholder with the downloaded message. Returns null when the placeholder is gone,
     * which happens when the same download finished twice.
     */
    fun saveRetrieved(conf: RetrieveConf, notification: MmsNotification, selfNumbers: List<String>, now: Long = System.currentTimeMillis()): Uri? {
        if (resolver.query(notification.uri, arrayOf(Mms._ID), null, null, null)?.use { it.count } != 1) return null
        val from = conf.from?.string?.let(::address) ?: PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR
        val to = conf.to.orEmpty().map { address(it.string) }
        val cc = conf.cc.orEmpty().map { address(it.string) }
        val recipients = threadRecipients(from, to + cc, selfNumbers)
        val sentSeconds = conf.date.takeIf { it > 0 } ?: (now / 1000)
        val values = ContentValues().apply {
            put(Mms.THREAD_ID, Threads.getOrCreateThreadId(context, recipients.toSet()))
            put(Mms.DATE, now / 1000)
            put(Mms.DATE_SENT, sentSeconds)
            put(Mms.READ, 0)
            put(Mms.SEEN, 0)
            put(Mms.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF)
            put(Mms.MMS_VERSION, conf.mmsVersion)
            put(Mms.TRANSACTION_ID, notification.transactionId)
            conf.messageId?.let { put(Mms.MESSAGE_ID, ascii(it)) }
            conf.contentType?.let { put(Mms.CONTENT_TYPE, ascii(it)) }
            putSubject(conf.subject)
            put(Mms.RETRIEVE_STATUS, PduHeaders.RETRIEVE_STATUS_OK)
            put(Mms.SUBSCRIPTION_ID, notification.subId)
        }
        val inserted = resolver.insert(Mms.Inbox.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(inserted)
        addAddress(id, from, PduHeaders.FROM)
        to.forEach { addAddress(id, it, PduHeaders.TO) }
        cc.forEach { addAddress(id, it, PduHeaders.CC) }
        val body = conf.body
        if (body != null) for (index in 0 until body.partsNum) addPart(id, body.getPart(index))
        resolver.delete(notification.uri, null, null)
        return ContentUris.withAppendedId(Mms.CONTENT_URI, id)
    }

    fun threadIdOf(message: Uri): Long? =
        resolver.query(message, arrayOf(Mms.THREAD_ID), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun isKnown(transactionId: String): Boolean =
        resolver.query(Mms.CONTENT_URI, arrayOf(Mms._ID), "${Mms.TRANSACTION_ID} = ?", arrayOf(transactionId), null)?.use { it.count > 0 } ?: false

    private fun addAddress(messageId: Long, address: String, type: Int) {
        val values = ContentValues().apply {
            put(Mms.Addr.ADDRESS, address)
            put(Mms.Addr.TYPE, type)
            put(Mms.Addr.CHARSET, CharacterSets.UTF_8)
        }
        resolver.insert(Uri.parse("content://mms/$messageId/addr"), values)
    }

    private fun addPart(messageId: Long, part: PduPart) {
        val type = part.contentType?.let(::ascii)?.lowercase() ?: return
        val values = ContentValues().apply {
            put(Mms.Part.CONTENT_TYPE, type)
            if (part.charset != 0) put(Mms.Part.CHARSET, part.charset)
            part.name?.let { put(Mms.Part.NAME, decoded(it, part.charset)) }
            part.filename?.let { put(Mms.Part.FILENAME, decoded(it, part.charset)) }
            part.contentId?.let { put(Mms.Part.CONTENT_ID, ascii(it)) }
            part.contentLocation?.let { put(Mms.Part.CONTENT_LOCATION, ascii(it)) }
            if (type in TEXT_TYPES) put(Mms.Part.TEXT, part.data?.let { decoded(it, part.charset) }.orEmpty())
        }
        val uri = resolver.insert(Uri.parse("content://mms/$messageId/part"), values) ?: return
        if (type !in TEXT_TYPES) part.data?.let { data -> resolver.openOutputStream(uri)?.use { it.write(data) } }
    }

    private fun ContentValues.putSubject(subject: EncodedStringValue?) {
        if (subject == null) return
        put(Mms.SUBJECT, subject.string)
        put(Mms.SUBJECT_CHARSET, subject.characterSet)
    }

    companion object {
        private val TEXT_TYPES = setOf("text/plain", "application/smil", "text/html")

        /** PDU addresses carry a type suffix such as "+79001234567/TYPE=PLMN". */
        fun address(raw: String): String = raw.substringBefore("/TYPE=").trim()

        /**
         * The sender plus everyone else the message went to, minus this phone. Without a known own number a
         * single recipient must be this phone, while a group thread keeps every recipient.
         */
        fun threadRecipients(from: String, recipients: List<String>, selfNumbers: List<String>): List<String> {
            val others = recipients.filterNot { recipient -> selfNumbers.any { sameNumber(it, recipient) } }
            val group = if (selfNumbers.isEmpty() && recipients.size <= 1) emptyList() else others
            return (listOf(from) + group.filterNot { sameNumber(it, from) }).distinctBy { it.filter(Char::isDigit).takeLast(NUMBER_DIGITS).ifEmpty { it } }
        }

        /**
         * Russian numbers come both as +7… and 8…, which PhoneNumberUtils.compare tells apart; the last ten digits,
         * as the phone book uses for duplicates, identify a number either way.
         */
        private fun sameNumber(a: String, b: String): Boolean {
            val x = a.filter(Char::isDigit)
            val y = b.filter(Char::isDigit)
            if (x.length < MIN_NUMBER_DIGITS || y.length < MIN_NUMBER_DIGITS) return a.equals(b, ignoreCase = true)
            return x.takeLast(NUMBER_DIGITS) == y.takeLast(NUMBER_DIGITS)
        }

        private const val NUMBER_DIGITS = 10
        private const val MIN_NUMBER_DIGITS = 7

        private fun ascii(bytes: ByteArray) = String(bytes, Charsets.ISO_8859_1)

        private fun decoded(bytes: ByteArray, charset: Int): String =
            (if (charset != 0) EncodedStringValue(charset, bytes) else EncodedStringValue(bytes)).string
    }
}
