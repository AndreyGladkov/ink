package io.github.andreygladkov.inkphone.dialer

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.ContactsContract
import android.view.MotionEvent
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
import io.github.andreygladkov.inkphone.ui.InkTextView
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowRoleManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class DialerScreenTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() {
        FakeContactsProvider.reset()
        FakeContactsProvider.addContact(1, "Иван Петров", phones = listOf("+7 701 111 22 33"))
        FakeContactsProvider.addContact(2, "Пётр Иванов", phones = listOf("+7 702 222 33 44"))
        listOf("Галина", "Гена", "Игорь", "Илья").forEachIndexed { i, name ->
            FakeContactsProvider.addContact(10L + i, name, phones = listOf("+7 705 000 00 0$i"))
        }
        Robolectric.setupContentProvider(FakeContactsProvider::class.java, ContactsContract.AUTHORITY)
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_CONTACTS)
        context.inkPrefs().edit { putString("last_tab", Tab.DIALER.name) }
    }

    @Test fun asksToBecomeDefaultDialer() {
        val root = contentRoot(Robolectric.buildActivity(MainActivity::class.java).setup().get())
        assertTrue(root.find<InkButton>().any { it.isShown && it.text.toString() == "Сделать по умолчанию" })
        assertPureBlackAndWhite(render(root))
    }

    @Test fun typingShowsT9SuggestionsAndTapFillsNumber() {
        Shadow.extract<ShadowRoleManager>(context.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_DIALER)
        val root = contentRoot(Robolectric.buildActivity(MainActivity::class.java).setup().get())
        press(root, '4')
        press(root, '2')
        press(root, '2')
        idleUntil { suggestions(root).size == 2 }
        assertEquals(listOf("Иван Петров\n+7 701 111 22 33", "Пётр Иванов\n+7 702 222 33 44"), suggestions(root))
        assertEquals("422", number(root))
        assertPureBlackAndWhite(render(root))
        root.find<InkButton>().first { it.text.toString().startsWith("Пётр Иванов") }.performClick()
        assertEquals("+7 702 222 33 44", number(root))
    }

    @Test fun fourSuggestionsFitAboveKeypad() {
        Shadow.extract<ShadowRoleManager>(context.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_DIALER)
        val root = contentRoot(Robolectric.buildActivity(MainActivity::class.java).setup().get())
        press(root, '4')
        idleUntil { suggestions(root).size == 4 }
        assertPureBlackAndWhite(render(root))
    }

    @Test fun defaultDialerAsksForCallPermissionsOnce() {
        Shadow.extract<ShadowRoleManager>(context.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_DIALER)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val requested = shadowOf(activity).lastRequestedPermission
        assertTrue(Manifest.permission.READ_PHONE_STATE in requested!!.requestedPermissions)
    }

    @Test fun longPressZeroTypesPlus() {
        val root = contentRoot(Robolectric.buildActivity(MainActivity::class.java).setup().get())
        val zero = key(root, '0')
        press(root, '0')
        zero.performLongClick()
        assertEquals("+", number(root))
    }

    @Test fun dialIntentPrefillsNumber() {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+77015550000"))
        val root = contentRoot(Robolectric.buildActivity(MainActivity::class.java, intent).setup().get())
        assertEquals("+77015550000", number(root))
    }

    private fun key(root: View, key: Char) = root.find<InkButton>().single { it.contentDescription == key.toString() }

    private fun press(root: View, key: Char) {
        val button = key(root, key)
        val now = SystemClock.uptimeMillis()
        button.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 5f, 5f, 0))
        button.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, 5f, 5f, 0))
    }

    private fun suggestions(root: View) = root.find<InkButton>().filter { it.isShown && it.contentDescription == null }
        .map { it.text.toString() }.filter { "\n+" in it }

    private fun number(root: View) = root.find<InkTextView>().first { it.isSingleLine() }.text.toString()

    private fun InkTextView.isSingleLine() = maxLines == 1 && this !is InkButton
}
