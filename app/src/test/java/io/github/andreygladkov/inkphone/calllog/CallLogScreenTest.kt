package io.github.andreygladkov.inkphone.calllog

import android.Manifest
import android.app.Application
import android.content.Context
import android.provider.CallLog
import android.provider.CallLog.Calls
import android.provider.ContactsContract
import android.view.View
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkphone.A9_QUALIFIERS
import io.github.andreygladkov.inkphone.MainActivity
import io.github.andreygladkov.inkphone.Tab
import io.github.andreygladkov.inkphone.assertPureBlackAndWhite
import io.github.andreygladkov.inkphone.contacts.FakeContactsProvider
import io.github.andreygladkov.inkphone.contentRoot
import io.github.andreygladkov.inkphone.find
import io.github.andreygladkov.inkphone.idleUntil
import io.github.andreygladkov.inkphone.inkPrefs
import io.github.andreygladkov.inkphone.render
import io.github.andreygladkov.inkphone.ui.InkButton
import io.github.andreygladkov.inkphone.ui.InkPagedList
import io.github.andreygladkov.inkphone.ui.InkTabBar
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class CallLogScreenTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = System.currentTimeMillis()

    @Before fun setUp() {
        FakeCallLogProvider.reset()
        FakeContactsProvider.reset()
        FakeCallLogProvider.add("+77011112233", Calls.MISSED_TYPE, now - 60_000, name = "Мама", unread = true)
        FakeCallLogProvider.add("+77011112233", Calls.MISSED_TYPE, now - 120_000, name = "Мама", unread = true)
        FakeCallLogProvider.add("+77020000000", Calls.OUTGOING_TYPE, now - 3_600_000, duration = 125)
        FakeCallLogProvider.add("", Calls.INCOMING_TYPE, now - 7_200_000)
        Robolectric.setupContentProvider(FakeCallLogProvider::class.java, CallLog.AUTHORITY)
        Robolectric.setupContentProvider(FakeContactsProvider::class.java, ContactsContract.AUTHORITY)
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG)
    }

    @Test fun groupsRepeatedMissedCallsAndRendersBlackAndWhite() {
        val root = openCallLog()
        val list = root.find<InkPagedList>().single { it.isShown }
        idleUntil { rows(list).isNotEmpty() }
        val rows = rows(list)
        assertTrue(rows[0].startsWith("Мама (2)\nпропущ."), rows.toString())
        assertTrue(rows[1].startsWith("+77020000000\nисх"), rows.toString())
        assertTrue(rows[2].startsWith("Неизвестный номер\nвх"), rows.toString())
        assertEquals(2, list.find<InkButton>().count { it.text.toString() == "Вызов" })
        assertTrue(!list.find<InkButton>().single { it.text.toString().startsWith("Неизвестный номер") }.isClickable)
        assertPureBlackAndWhite(render(root))
    }

    @Test fun missedFilterShowsOnlyMissed() {
        val root = openCallLog()
        val list = root.find<InkPagedList>().single { it.isShown }
        idleUntil { rows(list).isNotEmpty() }
        root.find<InkButton>().single { it.text.toString() == "Пропущенные" }.performClick()
        assertEquals(1, rows(list).size)
    }

    @Test fun openingLogMarksMissedCallsReadAndClearsBadge() {
        context.inkPrefs().edit { putString("last_tab", Tab.DIALER.name) }
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val tabs = contentRoot(activity).find<InkTabBar>().single()
        val logTab = tabs.find<InkButton>()[Tab.CALL_LOG.ordinal]
        idleUntil { logTab.badge == "2" && contentRoot(activity).find<InkPagedList>().any { rows(it).isNotEmpty() } }
        assertTrue(FakeCallLogProvider.calls.count { it[Calls.NEW] == 1 } == 2, "prepared log must not mark calls read")
        tabs.select(Tab.CALL_LOG)
        idleUntil { logTab.badge == null }
        assertTrue(FakeCallLogProvider.calls.none { it[Calls.NEW] == 1 })
    }

    @Test fun returningToLogKeepsItsPagesWhenNothingChanged() {
        context.inkPrefs().edit { putString("last_tab", Tab.CALL_LOG.name) }
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = contentRoot(activity)
        val list = root.find<InkPagedList>().single { it.isShown }
        idleUntil { rows(list).isNotEmpty() }
        val firstRow = list.find<InkButton>().first { "\n" in it.text.toString() }
        val tabs = root.find<InkTabBar>().single()
        tabs.select(Tab.CONTACTS)
        tabs.select(Tab.CALL_LOG)
        idleUntil { FakeCallLogProvider.calls.none { it[Calls.NEW] == 1 } }
        Thread.sleep(700)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(firstRow === list.find<InkButton>().first { "\n" in it.text.toString() }, "list was rebuilt")
    }

    private fun openCallLog(): View {
        context.inkPrefs().edit { putString("last_tab", Tab.CALL_LOG.name) }
        return contentRoot(Robolectric.buildActivity(MainActivity::class.java).setup().get())
    }

    private fun rows(list: InkPagedList) = list.find<InkButton>().map { it.text.toString() }.filter { "\n" in it }

}
