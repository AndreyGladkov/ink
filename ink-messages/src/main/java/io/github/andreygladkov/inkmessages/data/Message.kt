package io.github.andreygladkov.inkmessages.data

import android.content.ContentUris
import android.net.Uri

enum class SendStatus { NONE, SENDING, FAILED }

data class Attachment(val partId: Long, val contentType: String, val name: String? = null) {
    enum class Kind { IMAGE, CONTACT, AUDIO, VIDEO, OTHER }

    val kind: Kind
        get() = when {
            contentType.startsWith("image/") -> Kind.IMAGE
            contentType in CONTACT_TYPES -> Kind.CONTACT
            contentType.startsWith("audio/") -> Kind.AUDIO
            contentType.startsWith("video/") -> Kind.VIDEO
            else -> Kind.OTHER
        }

    val uri: Uri get() = ContentUris.withAppendedId(TelephonyStore.MMS_PARTS, partId)

    private companion object {
        val CONTACT_TYPES = setOf("text/x-vcard", "text/vcard", "text/directory")
    }
}

/** An MMS the carrier announced but whose content has not been downloaded yet. */
data class PendingMms(val sizeBytes: Long, val failed: Boolean)

data class Message(
    val id: Long,
    val isMms: Boolean,
    val threadId: Long,
    val date: Long,
    val text: String,
    val isOutgoing: Boolean,
    val isUnread: Boolean,
    val attachments: List<Attachment> = emptyList(),
    val subId: Int = -1,
    val status: SendStatus = SendStatus.NONE,
    val pending: PendingMms? = null,
) {
    val key: Long get() = id * 2 + if (isMms) 1 else 0
}
