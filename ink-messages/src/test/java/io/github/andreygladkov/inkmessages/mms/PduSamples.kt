package io.github.andreygladkov.inkmessages.mms

import io.github.andreygladkov.inkmessages.TestData.context
import io.github.andreygladkov.inkmessages.mmslib.pdu.CharacterSets
import io.github.andreygladkov.inkmessages.mmslib.pdu.EncodedStringValue
import io.github.andreygladkov.inkmessages.mmslib.pdu.PduBody
import io.github.andreygladkov.inkmessages.mmslib.pdu.PduComposer
import io.github.andreygladkov.inkmessages.mmslib.pdu.PduHeaders
import io.github.andreygladkov.inkmessages.mmslib.pdu.PduPart
import io.github.andreygladkov.inkmessages.mmslib.pdu.SendReq
import java.io.ByteArrayOutputStream

/** Binary PDUs as a carrier's MMSC sends them, so tests go through the real parser. */
object PduSamples {
    /**
     * Encoded by hand per OMA-MMS-ENC: the AOSP composer writes X-Mms-Message-Size like the relative Expiry,
     * which no carrier does and the parser reads back as a different number.
     */
    fun notification(from: String, transactionId: String, location: String, sizeBytes: Long = 48_000): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(PduHeaders.MESSAGE_TYPE)
        out.write(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND)
        out.write(PduHeaders.TRANSACTION_ID)
        out.writeText(transactionId)
        out.write(PduHeaders.MMS_VERSION)
        out.write(PduHeaders.CURRENT_MMS_VERSION or 0x80)
        val address = "$from/TYPE=PLMN"
        out.write(PduHeaders.FROM)
        out.write(address.length + 2)
        out.write(PduHeaders.FROM_ADDRESS_PRESENT_TOKEN)
        out.writeText(address)
        out.write(PduHeaders.MESSAGE_CLASS)
        out.write(PduHeaders.MESSAGE_CLASS_PERSONAL)
        out.write(PduHeaders.MESSAGE_SIZE)
        out.writeLongInteger(sizeBytes)
        val expiry = longInteger(7 * 24 * 3600L)
        out.write(PduHeaders.EXPIRY)
        out.write(expiry.size + 1)
        out.write(PduHeaders.VALUE_RELATIVE_TOKEN)
        out.write(expiry)
        out.write(PduHeaders.CONTENT_LOCATION)
        out.writeText(location)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeText(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
        write(0)
    }

    private fun ByteArrayOutputStream.writeLongInteger(value: Long) = write(longInteger(value))

    private fun longInteger(value: Long): ByteArray {
        val octets = generateSequence(value) { it ushr 8 }.takeWhile { it != 0L }.map { (it and 0xFF).toByte() }.toList().reversed()
        return byteArrayOf(octets.size.toByte()) + octets.toByteArray()
    }

    /**
     * The composer cannot write a retrieve-conf, but a send-req carries the same headers and body;
     * only the message type octet that opens every PDU differs.
     */
    fun retrieved(from: String, to: List<String>, dateSeconds: Long, parts: List<Part>): ByteArray {
        val req = SendReq().apply {
            this.from = EncodedStringValue("$from/TYPE=PLMN")
            to.forEach { addTo(EncodedStringValue("$it/TYPE=PLMN")) }
            date = dateSeconds
            transactionId = "retrieve".toByteArray()
            body = PduBody().apply {
                parts.forEachIndexed { index, part ->
                    addPart(PduPart().apply {
                        contentType = part.type.toByteArray()
                        contentLocation = "part$index".toByteArray()
                        contentId = "<part$index>".toByteArray()
                        part.name?.let { name = it.toByteArray() }
                        if (part.type.startsWith("text/")) charset = CharacterSets.UTF_8
                        data = part.data
                    })
                }
            }
        }
        val bytes = PduComposer(context, req).make()
        check(bytes[0] == MESSAGE_TYPE_HEADER && bytes[1] == PduHeaders.MESSAGE_TYPE_SEND_REQ.toByte())
        bytes[1] = PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF.toByte()
        return bytes
    }

    data class Part(val type: String, val data: ByteArray, val name: String? = null)

    fun text(value: String) = Part("text/plain", value.toByteArray())

    private const val MESSAGE_TYPE_HEADER = PduHeaders.MESSAGE_TYPE.toByte()
}
