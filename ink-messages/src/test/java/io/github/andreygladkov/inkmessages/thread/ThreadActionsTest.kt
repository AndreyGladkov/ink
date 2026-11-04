package io.github.andreygladkov.inkmessages.thread

import android.content.ClipboardManager
import android.content.Intent
import android.os.Looper
import android.provider.ContactsContract
import android.view.View
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkmessages.TestData
import io.github.andreygladkov.inkmessages.TestData.INBOX
import io.github.andreygladkov.inkmessages.TestData.SENT
import io.github.andreygladkov.inkmessages.TestData.context
import io.github.andreygladkov.inkmessages.compose.ComposeActivity
import io.github.andreygladkov.inkmessages.contacts.Blocking
import io.github.andreygladkov.inkmessages.contacts.ContactDirectory
import io.github.andreygladkov.inkmessages.conversations.ConversationRepository
import io.github.andreygladkov.inkmessages.data.FakeBlockedNumberProvider
import io.github.andreygladkov.inkmessages.data.FakePhoneLookupProvider
import io.github.andreygladkov.inkmessages.data.FakeTelephonyProvider
import io.github.andreygladkov.inkmessages.data.TelephonyStore
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class ThreadActionsTest {
    private val now = System.currentTimeMillis()
    private var thread = 0L
    private var question = 0L

    @Before fun setUp() {
        TestData.installProviders()
        thread = FakeTelephonyProvider.thread(NUMBER)
        FakeTelephonyProvider.sms(thread, "Привет", now - 60_000, INBOX)
        FakeTelephonyProvider.sms(thread, "Привет, как дела?", now - 50_000, SENT)
        question = FakeTelephonyProvider.sms(thread, "Придёшь сегодня?", now - 40_000, INBOX)
    }

    @Test fun callOpensTheDialerWithTheNumber() {
        val activity = open()
        button(activity, description = "Позвонить").performClick()
        val dial = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, dial.action)
        assertEquals("tel", dial.data!!.scheme)
        assertEquals(NUMBER, dial.data!!.schemeSpecificPart)
    }

    @Test fun theNameOfASavedContactOpensItsCard() {
        FakePhoneLookupProvider.add(NUMBER, "Мама", starred = true)
        val activity = open()
        title(activity).performClick()
        val view = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(ContactsContract.AUTHORITY, view.data!!.authority)
        assertTrue(view.data!!.pathSegments.containsAll(listOf("lookup", "lookup-1")), view.data.toString())
    }

    @Test fun anUnsavedNumberOffersToAddIt() {
        val activity = open()
        title(activity).performClick()
        val insert = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_INSERT, insert.action)
        assertEquals(NUMBER, insert.getStringExtra(ContactsContract.Intents.Insert.PHONE))
    }

    @Test fun markingUnreadFlagsTheLatestIncomingMessageAndLeaves() {
        TestData.makeDefaultSmsApp()
        val activity = open()
        idleUntil { FakeTelephonyProvider.sms.all { it["read"] == 1 } }
        button(activity, description = "Ещё").performClick()
        button(activity, "Непрочитано").performClick()
        idleUntil { row(question)["read"] == 0 }
        assertTrue(activity.isFinishing)
    }

    @Test fun blockingHidesTheConversationAndUnblockingIsOffered() {
        TestData.makeDefaultSmsApp()
        val activity = open()
        button(activity, description = "Ещё").performClick()
        button(activity, "Заблокировать").performClick()
        idleUntil { FakeBlockedNumberProvider.numbers == listOf(NUMBER) }
        assertTrue(activity.isFinishing)
        val repository = ConversationRepository(TelephonyStore(context.contentResolver), ContactDirectory(context.contentResolver), Blocking(context)::isBlocked)
        assertTrue(repository.load().none { it.threadId == thread })

        val again = open()
        button(again, description = "Ещё").performClick()
        button(again, "Разблокировать").performClick()
        idleUntil { FakeBlockedNumberProvider.numbers.isEmpty() }
        assertEquals("Заблокировать", button(again, "Заблокировать").text.toString())
    }

    @Test fun deletingTheConversationTakesASecondTap() {
        TestData.makeDefaultSmsApp()
        val activity = open()
        button(activity, description = "Ещё").performClick()
        val delete = button(activity, "Удалить переписку")
        delete.performClick()
        assertEquals("Точно удалить?", delete.text.toString())
        assertEquals(3, FakeTelephonyProvider.sms.size)
        delete.performClick()
        idleUntil { FakeTelephonyProvider.sms.isEmpty() }
        assertTrue(activity.isFinishing)
    }

    @Test fun longPressSelectsAMessageAndCancelBringsTheComposeBarBack() {
        TestData.makeDefaultSmsApp()
        val activity = open()
        val row = bubbleRow(activity, "Придёшь сегодня?")
        row.performLongClick()
        assertTrue(row.isHighlighted)
        assertFalse(contentRoot(activity).find<ComposeBar>().single().isShown)
        assertEquals(listOf("Копировать", "Переслать", "Удалить", "Отмена"), actionButtons(activity).map { it.text.toString() })

        button(activity, "Отмена").performClick()
        assertFalse(row.isHighlighted)
        assertTrue(contentRoot(activity).find<ComposeBar>().single().isShown)
        assertTrue(actionButtons(activity).isEmpty())
    }

    @Test fun copyPutsTheTextOnTheClipboard() {
        val activity = open()
        bubbleRow(activity, "Придёшь сегодня?").performLongClick()
        button(activity, "Копировать").performClick()
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals("Придёшь сегодня?", clip.getItemAt(0).text.toString())
    }

    @Test fun forwardOpensComposeWithTheText() {
        TestData.makeDefaultSmsApp()
        val activity = open()
        bubbleRow(activity, "Придёшь сегодня?").performLongClick()
        button(activity, "Переслать").performClick()
        val compose = shadowOf(activity).nextStartedActivity
        assertEquals(ComposeActivity::class.java.name, compose.component!!.className)
        assertEquals("Придёшь сегодня?", ComposeActivity.bodyOf(compose))
    }

    @Test fun deletingAMessageTakesASecondTap() {
        TestData.makeDefaultSmsApp()
        val activity = open()
        bubbleRow(activity, "Придёшь сегодня?").performLongClick()
        val delete = button(activity, "Удалить")
        delete.performClick()
        assertEquals("Удалить?", delete.text.toString())
        delete.performClick()
        idleUntil { FakeTelephonyProvider.sms.none { it["_id"] == question } }
        idleUntil {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
            bubbleRows(activity).none { it.find<BubbleView>().any { bubble -> bubble.text.toString() == "Придёшь сегодня?" } }
        }
        assertEquals(2, FakeTelephonyProvider.sms.size)
    }

    @Test fun withoutTheSmsRoleOnlyCopyingIsPossible() {
        val activity = open()
        button(activity, description = "Ещё").performClick()
        assertTrue(listOf("Непрочитано", "Заблокировать", "Удалить переписку").none { button(activity, it).isEnabled })
        bubbleRow(activity, "Придёшь сегодня?").performLongClick()
        assertEquals(listOf(true, false, false, true), actionButtons(activity).map(View::isEnabled))
    }

    private fun open(): ThreadActivity {
        val activity = Robolectric.buildActivity(ThreadActivity::class.java, ThreadActivity.intent(context, thread)).setup().get()
        idleUntil { bubbleRows(activity).size == 3 }
        return activity
    }

    private fun bubbleRows(activity: ThreadActivity) = contentRoot(activity).find<BubbleRow>()

    private fun title(activity: ThreadActivity) = contentRoot(activity).find<InkTextView>().first { it.isBold && it.hasOnClickListeners() }

    private fun bubbleRow(activity: ThreadActivity, text: String) =
        bubbleRows(activity).single { row -> row.find<BubbleView>().any { it.text.toString() == text } }

    private fun button(activity: ThreadActivity, text: String? = null, description: String? = null): InkButton =
        contentRoot(activity).find<InkButton>().single { (text == null || it.text.toString() == text) && (description == null || it.contentDescription == description) }

    private fun actionButtons(activity: ThreadActivity) = contentRoot(activity).find<InkButton>().filter { it.isShown && it.text.toString() in ACTIONS }

    private fun row(id: Long) = FakeTelephonyProvider.sms.single { it["_id"] == id }

    private companion object {
        const val NUMBER = "+79001112233"
        val ACTIONS = setOf("Копировать", "Переслать", "Удалить", "Удалить?", "Отмена")
    }
}
