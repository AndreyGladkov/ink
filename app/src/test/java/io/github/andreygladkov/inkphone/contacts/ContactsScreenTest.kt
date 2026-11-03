package io.github.andreygladkov.inkphone.contacts

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.os.VibratorManager
import android.provider.ContactsContract
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkphone.A9_QUALIFIERS
import io.github.andreygladkov.inkphone.MainActivity
import io.github.andreygladkov.inkphone.Tab
import io.github.andreygladkov.inkphone.assertPureBlackAndWhite
import io.github.andreygladkov.inkphone.contentRoot
import io.github.andreygladkov.inkphone.eink.InkStyle
import io.github.andreygladkov.inkphone.find
import io.github.andreygladkov.inkphone.idleUntil
import io.github.andreygladkov.inkphone.inkPrefs
import io.github.andreygladkov.inkphone.render
import io.github.andreygladkov.inkphone.ui.InkButton
import io.github.andreygladkov.inkphone.ui.InkEditText
import io.github.andreygladkov.inkphone.ui.InkPagedList
import io.github.andreygladkov.inkphone.ui.LetterIndexView
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class ContactsScreenTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val names = listOf(
        "Анна Ахматова", "Борис Пастернак", "Вера Холодная", "Глеб Жеглов", "Дмитрий Менделеев", "Ёлка Новогодняя",
        "Жанна Агузарова", "Зинаида Гиппиус", "Игорь Северянин", "Йоган Штраус", "Константин Циолковский-Длиннофамильный",
        "Лев Толстой", "Марина Цветаева", "Николай Гоголь", "Ольга Берггольц", "Пётр Чайковский", "Роза Сябитова",
        "Сергей Есенин", "Татьяна Ларина", "Ульяна Громова", "Фёдор Достоевский", "Юрий Гагарин", "Яков Перельман",
        "Alan Turing", "Émile Zola", "112",
    )

    @Before fun setUp() {
        FakeContactsProvider.reset()
        names.forEachIndexed { i, name ->
            val alternative = name.split(" ").let { if (it.size == 2) "${it[1]}, ${it[0]}" else name }
            FakeContactsProvider.addContact(i + 1L, name, phones = listOf("+7701000${"%04d".format(i)}"), alternative = alternative)
        }
        FakeContactsProvider.addContact(100, "Без Номера")
        Robolectric.setupContentProvider(FakeContactsProvider::class.java, ContactsContract.AUTHORITY)
        context.inkPrefs().edit { putString("last_tab", Tab.CONTACTS.name) }
    }

    @Test fun asksForPermissionWithoutAccess() {
        val root = contentRoot(launch())
        assertTrue(root.find<InkButton>().any { it.isShown && it.text.toString() == "Разрешить доступ" })
        assertPureBlackAndWhite(render(root))
    }

    @Test fun listsContactsWithPhonesOnPages() {
        grantContacts()
        val root = contentRoot(launch())
        val list = awaitList(root)
        assertPureBlackAndWhite(render(root))
        assertTrue(list.pageCount > 1)
        assertEquals("1 / ${list.pageCount}", pageLabel(root))
        assertEquals("Анна Ахматова", rows(list).first())
        assertTrue("Без Номера" !in allRows(list))
        assertEquals(names.size, allRows(list).size)
    }

    @Test fun searchBarHasCompactIconsAndSmallerHint() {
        grantContacts()
        val root = contentRoot(launch())
        awaitList(root)
        val density = context.resources.displayMetrics.density
        val search = root.find<InkEditText>().single { it.isShown }
        val bar = search.parent as View
        val icons = bar.find<InkButton>().filter { it.isShown }
        assertEquals(listOf("Новый контакт", "Настройки"), icons.map { it.contentDescription.toString() })
        icons.forEach { assertTrue(it.width >= 56 * density && it.height >= 56 * density, "${it.contentDescription}: ${it.width}x${it.height}") }
        val hint = search.hint as Spanned
        assertEquals(InkStyle.SECONDARY_SCALE, hint.getSpans(0, hint.length, RelativeSizeSpan::class.java).single().sizeChange)
        assertPureBlackAndWhite(render(root))
    }

    @Test fun pagerSpansFullWidthBelowIndex() {
        grantContacts()
        val root = contentRoot(launch())
        val list = awaitList(root)
        render(root)
        val label = root.find<TextView>().single { it.isShown && it.text.toString() == "1 / ${list.pageCount}" }
        val index = root.find<LetterIndexView>().single()
        val labelOnScreen = IntArray(2).also { label.getLocationInWindow(it) }
        val indexOnScreen = IntArray(2).also { index.getLocationInWindow(it) }
        assertEquals(root.width / 2f, labelOnScreen[0] + label.width / 2f, 1f)
        assertTrue(indexOnScreen[1] + index.height <= labelOnScreen[1])
    }

    @Test fun dividersStopAsFarFromIndexAsFromScreenEdge() {
        grantContacts()
        val root = contentRoot(launch())
        awaitList(root)
        val bitmap = render(root)
        val index = root.find<LetterIndexView>().single()
        val indexLeft = IntArray(2).also { index.getLocationInWindow(it) }[0]
        val inset = (InkButton.PADDING_DP * context.resources.displayMetrics.density).roundToInt()
        val dot = context.resources.displayMetrics.density.roundToInt()
        val period = dot * 3
        val dotted = (0 until bitmap.height).first { y ->
            (0 until inset).all { bitmap.getPixel(it, y) == Color.WHITE } &&
                (0..4).all { bitmap.getPixel(inset + it * period, y) == Color.BLACK && bitmap.getPixel(inset + it * period + dot, y) == Color.WHITE }
        }
        val inked = (0 until indexLeft).filter { bitmap.getPixel(it, dotted) == Color.BLACK }
        assertEquals(inset, inked.first())
        assertTrue(indexLeft - (inked.last() + 1) in inset..inset + period, "right gap ${indexLeft - inked.last() - 1}")
    }

    @Test fun indexJumpsToLetterPage() {
        grantContacts()
        val root = contentRoot(launch())
        val list = awaitList(root)
        val index = root.find<LetterIndexView>().single()
        assertEquals("Ю", index.letters[index.letters.indexOf("Я") - 1])
        assertEquals(listOf("A", "E", "#"), index.letters.takeLast(3))
        render(root)
        val slots = index.slots(index.height)
        val slot = slots.indexOfFirst { "A" in it }
        val y = (slot + 0.5f) * index.height / slots.size
        val now = SystemClock.uptimeMillis()
        index.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, y, 0))
        index.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 10f, y, 0))
        assertEquals("Alan Turing", rows(list).first())
        assertEquals("A", index.current)
    }

    @Test fun everyLetterOpensAPageStartingWithIt() {
        grantContacts()
        val root = contentRoot(launch())
        val list = awaitList(root)
        val index = root.find<LetterIndexView>().single()
        render(root)
        index.letters.forEach { letter ->
            val slots = index.slots(index.height)
            val slot = slots.indexOfFirst { it.first() == letter }
            if (slot < 0) return@forEach
            val y = (slot + 0.5f) * index.height / slots.size
            val now = SystemClock.uptimeMillis()
            index.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, y, 0))
            index.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 10f, y, 0))
            assertEquals(letter, ContactList.letter(rows(list).first()), "tapped $letter")
            assertEquals(letter, index.current)
        }
    }

    @Test fun scrollModeListsEveryContactInBlackAndWhite() {
        context.inkPrefs().edit { putBoolean(io.github.andreygladkov.inkphone.ui.InkPagedList.PREF_SCROLL, true) }
        grantContacts()
        val root = contentRoot(launch())
        val listView = root.find<android.widget.ListView>().single { it.isShown }
        idleUntil { (listView.adapter?.count ?: 0) == names.size }
        render(root)
        assertTrue(root.find<InkButton>().any { it.isShown && it.text.toString() == "Анна Ахматова" })
        assertTrue(root.find<TextView>().none { it.isShown && it.text.toString().matches(Regex("\\d+ / .*")) })
        assertPureBlackAndWhite(render(root))
    }

    @Test fun editorReportsFailedSaveAndStaysOpen() {
        grantContactWrites()
        FakeContactsProvider.failInserts = true
        val activity = Robolectric.buildActivity(ContactEditActivity::class.java, ContactEditActivity.intentForNew(context, "+77015550000")).setup().get()
        val root = contentRoot(activity)
        idleUntil { root.find<TextView>().any { it.text.toString() == "Новый контакт" } }
        root.find<io.github.andreygladkov.inkphone.ui.InkEditText>().first().setText("Тест")
        root.find<InkButton>().single { it.text.toString() == "Сохранить" }.performClick()
        idleUntil { root.find<TextView>().any { it.isShown && it.text.toString() == "Не удалось сохранить изменения" } }
        assertTrue(!activity.isFinishing)
    }

    @Test fun numberCardWorksWithoutContactsAccess() {
        val activity = Robolectric.buildActivity(ContactCardActivity::class.java, ContactCardActivity.intentForNumber(context, "+77019998877")).setup().get()
        val root = contentRoot(activity)
        idleUntil { root.find<TextView>().any { it.text.toString() == "В контакты" } }
        assertTrue(root.find<TextView>().any { it.text.toString() == "+77019998877" })
    }

    @Test fun cardForHiddenNumberHasNoCallButton() {
        grantContacts()
        val activity = Robolectric.buildActivity(ContactCardActivity::class.java, ContactCardActivity.intentForNumber(context, "")).setup().get()
        val root = contentRoot(activity)
        idleUntil { root.find<TextView>().any { it.text.toString() == "Контакт не найден" } }
        assertTrue(root.find<InkButton>().none { it.text.toString() == "Вызов" })
    }

    @Test fun preparedContactsTabFollowsSettingsChanges() {
        grantContacts()
        context.inkPrefs().edit { putString("last_tab", Tab.DIALER.name) }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val root = contentRoot(controller.get())
        idleUntil { root.find<ContactsScreen>().isNotEmpty() && root.find<ContactsScreen>().single().find<InkButton>().any { it.text.toString() == "Анна Ахматова" } }
        context.inkPrefs().edit { putString(ContactListOptions.PREF_NAME_ORDER, NameOrder.FAMILY_FIRST.name) }
        controller.pause().resume()
        root.find<io.github.andreygladkov.inkphone.ui.InkTabBar>().single().select(Tab.CONTACTS)
        idleUntil { root.find<ContactsScreen>().single().find<InkButton>().any { it.isShown && it.text.toString() == "Ахматова, Анна" } }
    }

    @Test fun swipeAndButtonsFlipPages() {
        grantContacts()
        val root = contentRoot(launch())
        val list = awaitList(root)
        val first = rows(list)
        list.showPage(1)
        assertTrue(rows(list).first() !in first)
        assertEquals("2 / ${list.pageCount}", pageLabel(root))
        list.showPage(list.pageCount + 5)
        assertEquals(list.pageCount - 1, list.page)
        assertPureBlackAndWhite(render(root))
    }

    @Test fun swipeFlipsPageWithoutPressingOrVibrating() {
        grantContacts()
        val root = contentRoot(launch())
        val list = awaitList(root)
        render(root)
        val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
        val now = SystemClock.uptimeMillis()
        val x = 100f
        list.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, 600f, 0))
        list.dispatchTouchEvent(MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_MOVE, x, 400f, 0))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        list.dispatchTouchEvent(MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, x, 200f, 0))
        assertEquals(1, list.page)
        assertTrue(list.find<InkButton>().none { it.isPressed })
        assertFalse(shadowOf(vibrator).isVibrating)
    }

    @Test fun searchFiltersByNameAndNumber() {
        grantContacts()
        val root = contentRoot(launch())
        val list = awaitList(root)
        val search = root.find<InkEditText>().single()
        search.setText("лев тол")
        idleUntil { rows(list) == listOf("Лев Толстой") }
        search.setText("770 1000 0002")
        idleUntil { rows(list) == listOf("Вера Холодная") }
        search.setText("нет такого")
        idleUntil { root.find<TextView>().any { it.isShown && it.text.toString() == "Ничего не найдено" } }
        assertPureBlackAndWhite(render(root))
    }

    @Test fun cardShowsDetailsInBlackAndWhite() {
        FakeContactsProvider.addContact(
            200, "Константин Константинопольский-Длиннофамильный",
            phones = listOf("+7 701 111 22 33", "+7 702 444 55 66"),
            emails = listOf("konstantin@example.com"),
            defaultPhone = "+7 702 444 55 66",
        )
        grantContacts()
        val activity = Robolectric.buildActivity(ContactCardActivity::class.java, ContactCardActivity.intent(context, 200)).setup().get()
        val root = contentRoot(activity)
        idleUntil { root.find<TextView>().any { it.text.toString() == "+7 701 111 22 33" } }
        val texts = root.find<TextView>().map { it.text.toString() }
        assertTrue("Мобильный · по умолчанию" in texts, texts.toString())
        assertTrue("konstantin@example.com" in texts)
        assertTrue("Google · me@gmail.com" in texts)
        assertEquals(2, root.find<InkButton>().count { it.text.toString() == "Вызов" })
        assertTrue("В избранное" in texts)
        assertTrue("Изменить" in texts)
        assertEquals(1, root.find<InkButton>().count { it.text.toString() == "Сделать основным" })
        assertTrue(root.find<InkButton>().any { it.text.toString() == "9" })
        assertPureBlackAndWhite(render(root, height = 2400))
    }

    @Test fun cardActionLabelsFitOnOneLineAtEveryTextSize() {
        FakeContactsProvider.addContact(200, "Анна Ахматова", phones = listOf("+77011112233"), starred = true)
        grantContacts()
        InkStyle.TEXT_SIZES_SP.forEach { size ->
            context.inkPrefs().edit { putFloat(InkStyle.PREF_TEXT_SIZE_SP, size) }
            val activity = Robolectric.buildActivity(ContactCardActivity::class.java, ContactCardActivity.intent(context, 200)).setup().get()
            val root = contentRoot(activity)
            idleUntil { root.find<InkButton>().any { it.text.toString() == "Из избранного" } }
            render(root)
            val actions = root.find<InkButton>().filter { it.text.toString() in setOf("Из избранного", "Изменить", "Поделиться") }
            assertEquals(3, actions.size)
            actions.forEach { button ->
                val label = button.text.toString()
                assertEquals(label.length, button.layout.getLineEnd(0), "\"$label\" is cut at ${size}sp")
                assertTrue(button.layout.getLineWidth(0) <= button.width - button.totalPaddingLeft - button.totalPaddingRight, "\"$label\" overflows at ${size}sp")
            }
        }
    }

    @Test fun unknownNumberCardOffersToAddContact() {
        grantContacts()
        val activity = Robolectric.buildActivity(ContactCardActivity::class.java, ContactCardActivity.intentForNumber(context, "+77019998877")).setup().get()
        val root = contentRoot(activity)
        idleUntil { root.find<TextView>().any { it.text.toString() == "В контакты" } }
        assertPureBlackAndWhite(render(root))
    }

    @Test fun newContactEditorRendersBlackAndWhite() {
        grantContactWrites()
        val activity = Robolectric.buildActivity(ContactEditActivity::class.java, ContactEditActivity.intentForNew(context, "+77015550000")).setup().get()
        val root = contentRoot(activity)
        idleUntil { root.find<TextView>().any { it.text.toString() == "Новый контакт" } }
        val fields = root.find<io.github.andreygladkov.inkphone.ui.InkEditText>()
        assertEquals(listOf("", "", "+77015550000"), fields.map { it.text.toString() })
        fields.first().requestFocus()
        assertPureBlackAndWhite(render(root, height = 2400))
    }

    private fun grantContacts() = shadowOf(context as Application).grantPermissions(Manifest.permission.READ_CONTACTS)

    private fun grantContactWrites() = shadowOf(context as Application).grantPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

    private fun launch(): Activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun awaitList(root: View): InkPagedList {
        val list = root.find<InkPagedList>().single { it.isShown }
        idleUntil { list.find<InkButton>().any { it.text.isNotEmpty() } }
        return list
    }

    private fun rows(list: InkPagedList) = list.find<InkButton>().map { it.text.toString() }.filter { it.isNotEmpty() }

    private fun allRows(list: InkPagedList): List<String> {
        val all = (0 until list.pageCount).flatMap { list.showPage(it); rows(list) }
        list.showPage(0)
        return all
    }

    private fun pageLabel(root: View) = root.find<TextView>().map { it.text.toString() }.single { it.matches(Regex("\\d+ / \\d+")) }
}
