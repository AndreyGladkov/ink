package io.github.andreygladkov.inkmessages.sms

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.provider.Telephony
import android.provider.Telephony.TextBasedSmsColumns
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.inkmessages.Category
import io.github.andreygladkov.inkmessages.TestData
import io.github.andreygladkov.inkmessages.TestData.context
import io.github.andreygladkov.inkmessages.data.FakePhoneLookupProvider
import io.github.andreygladkov.inkmessages.data.FakeTelephonyProvider
import io.github.andreygladkov.inkmessages.notify.MessageNotifications
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class SmsFlowTest {
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before fun setUp() {
        TestData.installProviders()
        TestData.makeDefaultSmsApp()
        MessageNotifications.createChannels(context)
    }

    @Test fun incomingSmsIsStoredUnreadAndNotifiedOnItsCategoryChannel() {
        FakePhoneLookupProvider.add("27838890001", "Мама", starred = true)
        deliver(HELLO_PDU)
        val row = FakeTelephonyProvider.sms.single()
        assertEquals("27838890001", row["address"])
        assertEquals("hellohello", row["body"])
        assertEquals(0, row["read"])
        idleUntil { notifications.allNotifications.isNotEmpty() }
        val notification = notifications.allNotifications.single()
        assertEquals(MessageNotifications.channelId(Category.FAVORITES), notification.channelId)
        assertEquals(listOf("Ответить", "Прочитано"), notification.actions.map { it.title.toString() })
    }

    @Test fun senderOutsideContactsGoesToTheOtherChannel() {
        deliver(HELLO_PDU)
        idleUntil { notifications.allNotifications.isNotEmpty() }
        assertEquals(MessageNotifications.channelId(Category.OTHER), notifications.allNotifications.single().channelId)
    }

    @Test fun nothingIsNotifiedWhileTheThreadIsOpen() {
        val thread = FakeTelephonyProvider.thread("27838890001")
        ThreadState.visibleThreadId = thread
        try {
            deliver(HELLO_PDU)
            idleUntil { FakeTelephonyProvider.sms.isNotEmpty() }
            Thread.sleep(200)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(notifications.allNotifications.isEmpty())
        } finally {
            ThreadState.visibleThreadId = null
        }
    }

    @Test fun sendStoresTheMessageInTheOutboxAndFailsWithoutAnSmsService() {
        val uri = SmsSender.send(context, "+79001112233", "Привет", -1)!!
        val row = FakeTelephonyProvider.sms.single()
        assertEquals(uri.lastPathSegment, row["_id"].toString())
        assertEquals("+79001112233", row["address"])
        assertEquals("Привет", row["body"])
        assertEquals(TextBasedSmsColumns.MESSAGE_TYPE_FAILED, row["type"])
    }

    @Test fun sentResultMovesTheMessageToSent() {
        val uri = outbox()
        report(uri, Activity.RESULT_OK, lastPart = false)
        assertEquals(TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX, FakeTelephonyProvider.sms.single()["type"])
        report(uri, Activity.RESULT_OK, lastPart = true)
        assertEquals(TextBasedSmsColumns.MESSAGE_TYPE_SENT, FakeTelephonyProvider.sms.single()["type"])
    }

    @Test fun anyFailedPartMarksTheMessageFailed() {
        val uri = outbox()
        report(uri, android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE, lastPart = false)
        report(uri, Activity.RESULT_OK, lastPart = true)
        val row = FakeTelephonyProvider.sms.single()
        assertEquals(TextBasedSmsColumns.MESSAGE_TYPE_FAILED, row["type"])
        assertEquals(android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE, row["error_code"])
    }

    @Test fun nothingIsWrittenWithoutTheSmsRole() {
        shadowOf(context.getSystemService(android.app.role.RoleManager::class.java)).removeHeldRole(android.app.role.RoleManager.ROLE_SMS)
        assertNull(SmsSender.send(context, "+79001112233", "Привет", -1))
        assertTrue(FakeTelephonyProvider.sms.isEmpty())
    }

    private fun outbox(): android.net.Uri {
        val thread = FakeTelephonyProvider.thread("+79001112233")
        val id = FakeTelephonyProvider.sms(thread, "Привет", System.currentTimeMillis(), TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX)
        return android.content.ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id)
    }

    private fun report(uri: android.net.Uri, resultCode: Int, lastPart: Boolean) {
        val intent = Intent(context, SmsSentReceiver::class.java).setData(uri).putExtra(SmsSentReceiver.EXTRA_LAST_PART, lastPart)
        context.sendOrderedBroadcast(intent, null, null, null, resultCode, null, null)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun deliver(pdu: String) {
        val bytes = pdu.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val intent = Intent(Telephony.Sms.Intents.SMS_DELIVER_ACTION)
            .setClass(context, SmsDeliverReceiver::class.java)
            .putExtra("pdus", arrayOf<Any>(bytes))
            .putExtra("format", "3gpp")
        (context as Application).sendBroadcast(intent)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private companion object {
        const val HELLO_PDU = "07917283010010F5040BC87238880900F10000993092516195800AE8329BFD4697D9EC37"
    }
}
