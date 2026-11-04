package io.github.andreygladkov.inkmessages.thread

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkmessages.Category
import io.github.andreygladkov.inkmessages.R
import io.github.andreygladkov.inkmessages.TestData
import io.github.andreygladkov.inkmessages.TestData.INBOX
import io.github.andreygladkov.inkmessages.TestData.MMS_INBOX
import io.github.andreygladkov.inkmessages.TestData.SENT
import io.github.andreygladkov.inkmessages.TestData.context
import io.github.andreygladkov.inkmessages.data.FakePhoneLookupProvider
import io.github.andreygladkov.inkmessages.data.FakeTelephonyProvider
import io.github.andreygladkov.inkmessages.notify.MessageNotifications
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class ThreadActivityTest {
    private val now = System.currentTimeMillis()
    private var thread = 0L

    @Before fun setUp() {
        TestData.installProviders()
        FakePhoneLookupProvider.add("+79001112233", "Мама", starred = true)
        thread = FakeTelephonyProvider.thread("+79001112233")
        repeat(30) { index ->
            val date = now - 2 * 86_400_000 + index * 600_000L
            FakeTelephonyProvider.sms(thread, "Сообщение ${index + 1}", date, if (index % 2 == 0) INBOX else SENT)
        }
        FakeTelephonyProvider.mms(thread, (now - 60_000) / 1000, MMS_INBOX, text = "Смотри", image = true)
    }

    @Test fun opensOnTheNewestPageWithEarlierPagesAbove() {
        val activity = open()
        val root = contentRoot(activity)
        val texts = bubbles(activity)
        assertEquals("[Фото]\nСмотри", texts.last())
        assertTrue("Сообщение 1" !in texts, texts.toString())
        assertTrue(root.find<InkTextView>().any { it.text.toString() == "Сегодня" })
        assertEquals("Мама", root.find<InkTextView>().first { it.isBold && it.text.isNotEmpty() }.text.toString())
        val up = root.find<InkButton>().single { it.contentDescription == "Предыдущая страница" }
        val down = root.find<InkButton>().single { it.contentDescription == "Следующая страница" }
        assertTrue(up.visibility == android.view.View.VISIBLE && down.visibility != android.view.View.VISIBLE)
        assertPureBlackAndWhite(render(root))
    }

    @Test fun upButtonShowsEarlierMessages() {
        val activity = open()
        val root = contentRoot(activity)
        val up = root.find<InkButton>().single { it.contentDescription == "Предыдущая страница" }
        while (up.visibility == android.view.View.VISIBLE) up.performClick()
        assertEquals("Сообщение 1", bubbles(activity).first())
        assertEquals(android.view.View.VISIBLE, root.find<InkButton>().single { it.contentDescription == "Следующая страница" }.visibility)
    }

    @Test fun outgoingBubblesHaveTheThickerBorder() {
        val activity = open()
        val rows = contentRoot(activity).find<BubbleRow>()
        val outgoing = rows.first { it.gravity and android.view.Gravity.END == android.view.Gravity.END }
        val incoming = rows.first { it.gravity and android.view.Gravity.END != android.view.Gravity.END }
        val bitmap = render(contentRoot(activity))
        val outBubble = outgoing.find<BubbleView>().single()
        val inBubble = incoming.find<BubbleView>().single()
        val density = context.resources.displayMetrics.density
        assertEquals((1 * density).roundToInt(), borderPixels(inBubble, bitmap))
        assertEquals((2 * density).roundToInt(), borderPixels(outBubble, bitmap))
    }

    private fun borderPixels(bubble: BubbleView, bitmap: android.graphics.Bitmap): Int {
        val location = IntArray(2).also(bubble::getLocationInWindow)
        val x = location[0] + bubble.width / 2
        var y = location[1]
        var count = 0
        while (bitmap.getPixel(x, y) == android.graphics.Color.BLACK) { count++; y++ }
        return count
    }

    private fun open(): ThreadActivity {
        val activity = Robolectric.buildActivity(ThreadActivity::class.java, ThreadActivity.intent(context, thread)).setup().get()
        idleUntil { bubbles(activity).isNotEmpty() }
        return activity
    }

    private fun bubbles(activity: ThreadActivity) =
        contentRoot(activity).find<InkPagedList>().single().find<BubbleView>().map { it.text.toString() }

    @Test fun withoutTheSmsRoleThereIsNoComposeBar() {
        assertTrue(contentRoot(open()).find<ComposeBar>().none { it.isShown })
    }

    @Test fun sendingClearsTheFieldAndShowsTheMessage() {
        TestData.makeDefaultSmsApp()
        val activity = open()
        val bar = contentRoot(activity).find<ComposeBar>().single()
        assertTrue(bar.isShown)
        bar.field.setText("Еду")
        contentRoot(activity).find<InkButton>().single { it.contentDescription == "Отправить" }.performClick()
        assertEquals("", bar.field.text.toString())
        idleUntil {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600))
            bubbles(activity).lastOrNull() == "Еду"
        }
        assertTrue(contentRoot(activity).find<InkButton>().any { it.text.toString() == "Не отправлено — повторить" })
    }

    @Test fun counterAppearsForMessagesLongerThanOneSms() {
        TestData.makeDefaultSmsApp()
        val activity = open()
        val bar = contentRoot(activity).find<ComposeBar>().single()
        bar.field.setText("я".repeat(60))
        assertTrue(contentRoot(activity).find<InkTextView>().none { it.isShown && it.text.contains("SMS") })
        bar.field.setText("я".repeat(75))
        assertTrue(contentRoot(activity).find<InkTextView>().any { it.isShown && it.text.toString() == "2 SMS · осталось 59" })
    }

    @Test fun draftIsKeptWhenLeavingAndRestoredOnReturn() {
        TestData.makeDefaultSmsApp()
        val controller = Robolectric.buildActivity(ThreadActivity::class.java, ThreadActivity.intent(context, thread)).setup()
        contentRoot(controller.get()).find<ComposeBar>().single().field.setText("Наберу позже")
        controller.pause().stop().destroy()
        assertEquals("Наберу позже", contentRoot(open()).find<ComposeBar>().single().field.text.toString())
    }

    @Test fun openingTheThreadMarksItRead() {
        TestData.makeDefaultSmsApp()
        val unread = FakeTelephonyProvider.sms(thread, "Ты где?", now, INBOX, read = false)
        open()
        idleUntil { FakeTelephonyProvider.sms.single { it["_id"] == unread }["read"] == 1 }
    }

    @Test fun openingTheThreadClearsItsNotificationEvenWithNothingUnread() {
        TestData.makeDefaultSmsApp()
        val notifications = context.getSystemService(NotificationManager::class.java)
        MessageNotifications.createChannels(context)
        val replied = NotificationCompat.Builder(context, MessageNotifications.channelId(Category.FAVORITES))
            .setSmallIcon(R.drawable.ic_notification)
            .build()
        notifications.notify(thread.toInt(), replied)
        open()
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test fun twoActiveSimsOfferAChoice() {
        TestData.makeDefaultSmsApp()
        shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.READ_PHONE_STATE)
        val subscriptions = listOf(1 to "t2", 2 to "MTS").map { (slot, name) ->
            org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder.newBuilder().setId(slot + 10).setSimSlotIndex(slot - 1).setDisplayName(name).buildSubscriptionInfo()
        }
        shadowOf(context.getSystemService(android.telephony.SubscriptionManager::class.java)).setActiveSubscriptionInfoList(subscriptions)
        val activity = open()
        idleUntil { contentRoot(activity).find<InkButton>().any { it.text.toString() == "SIM 2 MTS" } }
        val labels = contentRoot(activity).find<ComposeBar>().single().find<InkButton>().map { it.text.toString() }
        assertEquals(listOf("SIM 1 t2", "SIM 2 MTS"), labels.filter { it.startsWith("SIM") })
    }
}
