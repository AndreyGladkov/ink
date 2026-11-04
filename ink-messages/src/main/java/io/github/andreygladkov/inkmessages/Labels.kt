package io.github.andreygladkov.inkmessages

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import io.github.andreygladkov.inkmessages.contacts.ContactDirectory
import io.github.andreygladkov.inkmessages.contacts.Participant
import io.github.andreygladkov.inkmessages.data.Attachment
import io.github.andreygladkov.inkmessages.data.Message
import java.util.Locale

class Labels(private val context: Context) {
    private val countryIso = context.getSystemService(TelephonyManager::class.java)?.networkCountryIso?.takeIf(String::isNotEmpty)?.uppercase()
        ?: Locale.getDefault().country

    fun title(participants: List<Participant>): String = participants.joinToString(", ") { name(it) }

    fun name(participant: Participant): String = participant.name ?: address(participant.address)

    fun address(address: String): String =
        if (ContactDirectory.isPhoneNumber(address)) PhoneNumberUtils.formatNumber(address, countryIso) ?: address else address

    fun attachments(message: Message): List<String> {
        if (message.pending != null) return listOf(context.getString(R.string.attachment_mms))
        return message.attachments.map { context.getString(attachmentLabel(it.kind)) }
    }

    private fun attachmentLabel(kind: Attachment.Kind) = when (kind) {
        Attachment.Kind.IMAGE -> R.string.attachment_image
        Attachment.Kind.CONTACT -> R.string.attachment_contact
        Attachment.Kind.AUDIO -> R.string.attachment_audio
        Attachment.Kind.VIDEO -> R.string.attachment_video
        Attachment.Kind.OTHER -> R.string.attachment_other
    }

    fun preview(message: Message): String {
        val body = (attachments(message) + listOf(message.text).filter(String::isNotBlank)).joinToString(" ")
        return if (message.isOutgoing) context.getString(R.string.you_prefix, body) else body
    }
}
