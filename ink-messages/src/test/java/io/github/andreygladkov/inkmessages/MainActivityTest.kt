package io.github.andreygladkov.inkmessages

import android.Manifest
import android.app.Application
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTabBar
import io.github.andreygladkov.inkmessages.TestData.INBOX
import io.github.andreygladkov.inkmessages.TestData.MMS_INBOX
import io.github.andreygladkov.inkmessages.TestData.SENT
import io.github.andreygladkov.inkmessages.TestData.context
import io.github.andreygladkov.inkmessages.conversations.ConversationListScreen
import io.github.andreygladkov.inkmessages.conversations.ConversationRow
import io.github.andreygladkov.inkmessages.data.FakePhoneLookupProvider
import io.github.andreygladkov.inkmessages.data.FakeTelephonyProvider
import io.github.andreygladkov.inkmessages.thread.ThreadActivity
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class MainActivityTest {
    private val now = System.currentTimeMillis()
    private var motherThread = 0L

    @Before fun setUp() {
        TestData.installProviders()
        FakePhoneLookupProvider.add("+79001112233", "Мама", starred = true)
        FakePhoneLookupProvider.add("+79004445566", "Петя")
        motherThread = FakeTelephonyProvider.thread("+79001112233")
        FakeTelephonyProvider.sms(motherThread, "Ты где?", now - 600_000, SENT)
        FakeTelephonyProvider.sms(motherThread, "Позвони мне", now - 60_000, INBOX, read = false)
        FakeTelephonyProvider.sms(FakeTelephonyProvider.thread("+79004445566"), "Буду в 7", now - 3_600_000, SENT)
        FakeTelephonyProvider.sms(FakeTelephonyProvider.thread("Kaspi"), "Код 1234", now - 7_200_000, INBOX)
        FakeTelephonyProvider.mms(FakeTelephonyProvider.thread("900"), (now - 2 * 86_400_000) / 1000, MMS_INBOX, image = true)
        FakeTelephonyProvider.thread("+79007778899")
    }

    @Test fun allCategoryShowsEveryConversationNewestFirstInBlackAndWhite() {
        val activity = open()
        val rows = rows(activity)
        assertEquals(listOf("Мама (1)", "Петя", "Kaspi", "900"), rows.map { it.title.text.toString() })
        assertEquals(listOf("Позвони мне", "Я: Буду в 7", "Код 1234", "[Фото]"), rows.map { it.preview.text.toString() })
        assertTrue(rows[0].title.isBold && !rows[1].title.isBold)
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun categoriesFilterConversations() {
        val activity = open()
        val tabs = tabBar(activity)
        tabs.select(Category.FAVORITES)
        assertEquals(listOf("Мама (1)"), titles(activity))
        tabs.select(Category.CONTACTS)
        assertEquals(listOf("Мама (1)", "Петя"), titles(activity))
        tabs.select(Category.OTHER)
        assertEquals(listOf("Kaspi", "900"), titles(activity))
    }

    @Test fun tabBadgesCountConversationsWithUnreadMessages() {
        val activity = open()
        val badges = tabBar(activity).find<InkButton>().map { it.badge }
        assertEquals(listOf("1", "1", null, "1"), badges)
    }

    @Test fun opensTheConfiguredCategory() {
        context.inkPrefs().edit { putString(StartCategory.PREF, Category.OTHER.name) }
        assertEquals(Category.OTHER, tabBar(open()).selected)
    }

    @Test fun canReopenTheLastCategory() {
        context.inkPrefs().edit { putString(StartCategory.PREF, StartCategory.LAST_OPENED) }
        tabBar(open()).select(Category.CONTACTS)
        assertEquals(Category.CONTACTS, tabBar(open()).selected)
    }

    @Test fun rowOpensTheThread() {
        val activity = open()
        rows(activity).first().performClick()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ThreadActivity::class.java.name, started.component?.className)
        assertEquals(motherThread, started.getLongExtra("thread_id", -1))
    }

    @Test fun asksForAccessWithoutPermissions() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = contentRoot(activity)
        val grant = root.find<InkButton>().single { it.text.toString() == "Разрешить доступ" }
        assertTrue(grant.isShown)
        assertTrue(root.find<ConversationRow>().isEmpty())
        assertNull(tabBar(activity).find<InkButton>().firstNotNullOfOrNull { it.badge })
        assertPureBlackAndWhite(render(root))
    }

    private fun open(): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idleUntil { rows(activity).isNotEmpty() }
        return activity
    }

    private fun tabBar(activity: MainActivity) = contentRoot(activity).find<InkTabBar<Category>>().single()

    private fun visibleScreen(root: ViewGroup) = root.find<ConversationListScreen>().single { it.visibility == View.VISIBLE }

    private fun rows(activity: MainActivity) = visibleScreen(contentRoot(activity)).find<ConversationRow>()

    private fun titles(activity: MainActivity): List<String> {
        idleUntil { rows(activity).isNotEmpty() }
        return rows(activity).map { it.title.text.toString() }
    }

    @Test fun withoutTheSmsRoleOffersToBecomeDefaultAndHidesCompose() {
        val root = contentRoot(open())
        assertTrue(root.find<InkButton>().single { it.text.toString() == "Сделать основным" }.isShown)
        assertTrue(root.find<InkButton>().none { it.isShown && it.text.toString() == "Написать" })
    }

    @Test fun defaultAppShowsComposeAndDraftPreviews() {
        TestData.makeDefaultSmsApp()
        io.github.andreygladkov.inkmessages.sms.Drafts(context.inkPrefs()).set(motherThread, "Скоро буду")
        val activity = open()
        val root = contentRoot(activity)
        assertTrue(root.find<InkButton>().none { it.isShown && it.text.toString() == "Сделать основным" })
        assertTrue(root.find<InkButton>().single { it.text.toString() == "Написать" }.isShown)
        assertEquals("Черновик: Скоро буду", rows(activity).first().preview.text.toString())
    }
}
