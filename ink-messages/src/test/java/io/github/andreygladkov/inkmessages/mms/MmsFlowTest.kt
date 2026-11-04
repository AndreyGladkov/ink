package io.github.andreygladkov.inkmessages.mms

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.inkmessages.Category
import io.github.andreygladkov.inkmessages.TestData
import io.github.andreygladkov.inkmessages.TestData.context
import io.github.andreygladkov.inkmessages.data.Attachment
import io.github.andreygladkov.inkmessages.data.FakeTelephonyProvider
import io.github.andreygladkov.inkmessages.data.PendingMms
import io.github.andreygladkov.inkmessages.data.TelephonyStore
import io.github.andreygladkov.inkmessages.mmslib.pdu.NotifyRespInd
import io.github.andreygladkov.inkmessages.mmslib.pdu.PduHeaders
import io.github.andreygladkov.inkmessages.mmslib.pdu.PduParser
import io.github.andreygladkov.inkmessages.notify.MessageNotifications
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSmsManager
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class MmsFlowTest {
    private val sms: ShadowSmsManager get() = shadowOf(context.getSystemService(SmsManager::class.java))
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before fun setUp() {
        TestData.installProviders()
        TestData.makeDefaultSmsApp()
        MessageNotifications.createChannels(context)
    }

    @Test fun pushStoresAPlaceholderAndAsksTheSystemToDownloadIt() {
        push(PduSamples.notification(SENDER, "T1", LOCATION, sizeBytes = 120_000))
        val download = awaitDownload()
        val row = FakeTelephonyProvider.mms.single()
        assertEquals(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND, row["m_type"])
        assertEquals(LOCATION, row["ct_l"])
        assertEquals("T1", row["tr_id"])
        assertEquals(0, row["read"])
        assertEquals(listOf(SENDER), FakeTelephonyProvider.recipientsOf(row["thread_id"] as Long))
        assertEquals(LOCATION, download.locationUrl)
        assertEquals("${context.packageName}.mmsfiles", download.contentUri.authority)
        val pending = TelephonyStore(context.contentResolver).thread(row["thread_id"] as Long).single()
        assertEquals(PendingMms(120_000, failed = false), pending.pending)
    }

    @Test fun aRepeatedPushIsIgnored() {
        push(PduSamples.notification(SENDER, "T1", LOCATION))
        awaitDownload()
        sms.clearLastDownloadedMultimediaMessageParams()
        push(PduSamples.notification(SENDER, "T1", LOCATION))
        Thread.sleep(200)
        assertEquals(1, FakeTelephonyProvider.mms.size)
        assertNull(sms.lastDownloadedMultimediaMessageParams)
    }

    @Test fun downloadedMessageReplacesThePlaceholderIsAcknowledgedAndNotified() {
        push(PduSamples.notification(SENDER, "T1", LOCATION))
        val image = byteArrayOf(1, 2, 3, 4, 5)
        finishDownload(PduSamples.retrieved(SENDER, listOf(ME), 1_700_000_000, listOf(PduSamples.text("Привет"), PduSamples.Part("image/jpeg", image, "cat.jpg"))))
        idleUntil { FakeTelephonyProvider.mms.any { it["m_type"] == PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF } && notifications.allNotifications.isNotEmpty() }

        val row = FakeTelephonyProvider.mms.single()
        assertEquals(PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF, row["m_type"])
        assertEquals(1_700_000_000L, row["date_sent"])
        assertEquals(listOf(SENDER), FakeTelephonyProvider.recipientsOf(row["thread_id"] as Long))
        assertEquals(
            listOf(SENDER to PduHeaders.FROM, ME to PduHeaders.TO),
            FakeTelephonyProvider.addrs.filter { it["msg_id"] == row["_id"] }.map { it["address"] to it["type"] },
        )
        val message = TelephonyStore(context.contentResolver).thread(row["thread_id"] as Long).single()
        assertEquals("Привет", message.text)
        assertEquals(listOf(Attachment.Kind.IMAGE), message.attachments.map { it.kind })
        assertEquals("cat.jpg", message.attachments.single().name)
        assertContentEquals(image, context.contentResolver.openInputStream(message.attachments.single().uri)!!.use { it.readBytes() })
        assertNull(message.pending)
        assertEquals(MessageNotifications.channelId(Category.OTHER), notifications.allNotifications.single().channelId)

        idleUntil { sms.lastSentMultimediaMessageParams != null }
        val response = context.contentResolver.openInputStream(sms.lastSentMultimediaMessageParams.contentUri)!!.use { PduParser(it.readBytes(), true).parse() } as NotifyRespInd
        assertEquals("T1", String(response.transactionId))
        assertEquals(PduHeaders.STATUS_RETRIEVED, response.status)
    }

    @Test fun aFailedDownloadMarksThePlaceholderAndTellsTheUserOnce() {
        push(PduSamples.notification(SENDER, "T1", LOCATION))
        val download = awaitDownload()
        download.downloadedIntent!!.send(context, SmsManager.MMS_ERROR_HTTP_FAILURE, null)
        idleUntil { FakeTelephonyProvider.mms.single()["retr_st"] != null && notifications.allNotifications.isNotEmpty() }
        assertEquals(PendingMms(48_000, failed = true), TelephonyStore(context.contentResolver).thread(FakeTelephonyProvider.mms.single()["thread_id"] as Long).single().pending)
    }

    @Test fun aManualRetryStartsTheDownloadAgainAndClearsTheFailure() {
        val thread = FakeTelephonyProvider.thread(SENDER)
        val id = FakeTelephonyProvider.pendingMms(thread, 1_700_000_000, 10_000, failed = true)
        MmsDownloader.start(context, MmsStore(context).notification(id)!!, automatic = false)
        assertEquals("http://mmsc.example/$id", awaitDownload().locationUrl)
        assertNull(FakeTelephonyProvider.mms.single()["retr_st"])
        assertTrue(MmsDownloader.isActive(id))
    }

    @Test fun aGroupMessageGoesToTheThreadWithEveryoneElse() {
        push(PduSamples.notification(SENDER, "T1", LOCATION))
        finishDownload(PduSamples.retrieved(SENDER, listOf(OTHER, ANOTHER), 1_700_000_000, listOf(PduSamples.text("Всем привет"))))
        idleUntil { FakeTelephonyProvider.mms.any { it["m_type"] == PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF } }
        val thread = FakeTelephonyProvider.mms.single()["thread_id"] as Long
        assertEquals(setOf(SENDER, OTHER, ANOTHER), FakeTelephonyProvider.recipientsOf(thread).toSet())
    }

    private fun push(pdu: ByteArray) {
        val intent = Intent(Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION)
            .setClass(context, MmsPushReceiver::class.java)
            .setType("application/vnd.wap.mms-message")
            .putExtra("data", pdu)
        (context as Application).sendBroadcast(intent)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun awaitDownload(): ShadowSmsManager.DownloadMultimediaMessageParams {
        idleUntil { sms.lastDownloadedMultimediaMessageParams != null }
        return sms.lastDownloadedMultimediaMessageParams
    }

    private fun finishDownload(retrieved: ByteArray) {
        val download = awaitDownload()
        context.contentResolver.openOutputStream(download.contentUri)!!.use { it.write(retrieved) }
        download.downloadedIntent!!.send(context, Activity.RESULT_OK, null)
    }

    private companion object {
        const val SENDER = "+79001112233"
        const val ME = "+79000000001"
        const val OTHER = "+79005556677"
        const val ANOTHER = "+79008889900"
        const val LOCATION = "http://mmsc.example/T1"
    }
}
