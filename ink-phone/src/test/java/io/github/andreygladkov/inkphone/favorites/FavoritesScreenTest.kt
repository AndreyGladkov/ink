package io.github.andreygladkov.inkphone.favorites

import android.Manifest
import android.app.Application
import android.content.Context
import android.provider.ContactsContract
import android.view.View
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.inkphone.MainActivity
import io.github.andreygladkov.inkphone.Tab
import io.github.andreygladkov.inkphone.contacts.FakeContactsProvider
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class FavoritesScreenTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() {
        FakeContactsProvider.reset()
        FakeContactsProvider.addContact(1, "Мама", phones = listOf("+7 701 111 22 33"), starred = true)
        FakeContactsProvider.addContact(2, "Борис", phones = listOf("+7 702 222 33 44"))
        FakeContactsProvider.addContact(3, "Анна", phones = listOf("+7 703 333 44 55"), starred = true)
        Robolectric.setupContentProvider(FakeContactsProvider::class.java, ContactsContract.AUTHORITY)
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_CONTACTS)
        context.inkPrefs().edit { putString("last_tab", Tab.FAVORITES.name) }
    }

    @Test fun listsStarredContactsInBlackAndWhite() {
        val root = contentRoot(Robolectric.buildActivity(MainActivity::class.java).setup().get())
        val list = root.find<InkPagedList>().single { it.isShown }
        idleUntil { rows(list).isNotEmpty() }
        assertEquals(listOf("Анна\n+7 703 333 44 55", "Мама\n+7 701 111 22 33"), rows(list))
        listOf("SMS", "Вызов").forEach { action ->
            assertEquals(2, list.find<InkButton>().count { it.contentDescription == action && it.text.isEmpty() && !it.isBordered }, action)
        }
        assertPureBlackAndWhite(render(root))
    }

    @Test fun speedDialShowsAllKeys() {
        SpeedDial(context.inkPrefs()).assign('2', "+7 701 111 22 33", listOf("+7 701 111 22 33"))
        val root = contentRoot(Robolectric.buildActivity(MainActivity::class.java).setup().get())
        val list = root.find<InkPagedList>().single { it.isShown }
        root.find<InkButton>().single { it.text.toString() == "Быстрый набор" }.performClick()
        idleUntil { list.find<InkButton>().any { it.text.toString().startsWith("3 ·") } }
        val all = (0 until list.pageCount).flatMap { list.showPage(it); list.find<InkButton>().map { b -> b.text.toString() } }
        assertEquals(8, all.count { it.matches(Regex("^[2-9] ·.*", RegexOption.DOT_MATCHES_ALL)) })
        assertEquals("2 · Мама\n+7 701 111 22 33", all.first { it.startsWith("2 ·") })
        assertEquals("3 · не назначен", all.first { it.startsWith("3 ·") })
        assertPureBlackAndWhite(render(root))
    }

    private fun rows(list: View) = list.find<InkButton>().map { it.text.toString() }.filter { "\n" in it }
}
