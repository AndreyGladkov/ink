package io.github.andreygladkov.inkmessages.search

import android.os.Looper
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkmessages.TestData
import io.github.andreygladkov.inkmessages.TestData.INBOX
import io.github.andreygladkov.inkmessages.TestData.SENT
import io.github.andreygladkov.inkmessages.TestData.context
import io.github.andreygladkov.inkmessages.conversations.ConversationRow
import io.github.andreygladkov.inkmessages.data.FakePhoneLookupProvider
import io.github.andreygladkov.inkmessages.data.FakeTelephonyProvider
import io.github.andreygladkov.inkmessages.thread.BubbleRow
import io.github.andreygladkov.inkmessages.thread.BubbleView
import io.github.andreygladkov.inkmessages.thread.ThreadActivity
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class SearchActivityTest {
    private val now = System.currentTimeMillis()
    private var mom = 0L

    @Before fun setUp() {
        TestData.installProviders()
        FakePhoneLookupProvider.add("+79001112233", "Мама", starred = true)
        mom = FakeTelephonyProvider.thread("+79001112233")
        FakeTelephonyProvider.sms(mom, "Купи сыр и хлеб", now - 3 * 86_400_000L, INBOX)
        repeat(30) { FakeTelephonyProvider.sms(mom, "Сообщение ${it + 1}", now - 2 * 86_400_000L + it * 600_000L, if (it % 2 == 0) INBOX else SENT) }
        val bank = FakeTelephonyProvider.thread("Tinkoff")
        FakeTelephonyProvider.sms(bank, "Ваш код 1234", now - 60_000, INBOX)
    }

    @Test fun findsConversationsAndMessagesInSections() {
        val activity = searchFor("мама")
        assertEquals(listOf("Переписки"), sections(activity))
        assertEquals(listOf("Мама"), rows(activity).map { it.title.text.toString() })

        type(activity, "сыр")
        assertEquals(listOf("Сообщения"), sections(activity))
        assertEquals("Купи сыр и хлеб", rows(activity).single().preview.text.toString())
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun tellsWhenNothingIsFound() {
        val activity = searchFor("абракадабра")
        assertTrue(contentRoot(activity).find<InkTextView>().single { it.text.toString() == "Ничего не найдено" }.isShown)
    }

    @Test fun aFoundMessageOpensItsConversationOnThatMessage() {
        val activity = searchFor("сыр")
        rows(activity).single().performClick()
        val open = shadowOf(activity).nextStartedActivity
        val thread = Robolectric.buildActivity(ThreadActivity::class.java, open).setup().get()
        idleUntil { contentRoot(thread).find<BubbleRow>().isNotEmpty() }
        val found = contentRoot(thread).find<BubbleRow>().single { row -> row.find<BubbleView>().any { it.text.toString() == "Купи сыр и хлеб" } }
        assertTrue(found.isHighlighted)
        assertTrue(contentRoot(thread).find<InkButton>().single { it.contentDescription == "Следующая страница" }.isShown, "an older page is shown")
    }

    private fun searchFor(text: String): SearchActivity {
        val activity = Robolectric.buildActivity(SearchActivity::class.java, SearchActivity.intent(context)).setup().get()
        type(activity, text)
        return activity
    }

    private fun type(activity: SearchActivity, text: String) {
        contentRoot(activity).find<InkEditText>().single().setText(text)
        idleUntil {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            rows(activity).isNotEmpty() || contentRoot(activity).find<InkTextView>().any { it.isShown && it.text.toString() == "Ничего не найдено" }
        }
    }

    private fun rows(activity: SearchActivity) = contentRoot(activity).find<ConversationRow>()

    private fun sections(activity: SearchActivity) =
        contentRoot(activity).find<InkTextView>().filter { it.isBold && it.text.toString() in setOf("Переписки", "Сообщения") }.map { it.text.toString() }
}
