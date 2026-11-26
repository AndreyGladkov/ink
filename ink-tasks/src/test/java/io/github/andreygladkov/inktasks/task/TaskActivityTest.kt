package io.github.andreygladkov.inktasks.task

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.DayCell
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkDateActivity
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkTimeActivity
import io.github.andreygladkov.inktasks.Fixtures
import io.github.andreygladkov.inktasks.MainActivity
import io.github.andreygladkov.inktasks.remind.ReminderNotifications
import io.github.andreygladkov.inktasks.store.TaskLists
import io.github.andreygladkov.inktasks.tasks.TaskRef
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class TaskActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val home = "# Дом\n- [ ] Купить молоко 📅 2026-11-25\n\t2,5%\n\t- [ ] Заодно хлеб\n- [ ] Полить цветы\n- [x] Позвонить маме ✅ 2026-11-23\n"
    private val milk = TaskRef("- [ ] Купить молоко 📅 2026-11-25", 0)

    @Before fun setUp() {
        Fixtures.fixToday()
        Fixtures.write(context, "Дом", home)
    }

    @After fun restore() = Fixtures.restoreClock()

    @Test fun aTaskShowsItsFieldsDateAndSubtasks() {
        val activity = edit(milk).get()
        assertEquals("Купить молоко", fields(activity)[0].text.toString())
        assertEquals("2,5%", fields(activity)[1].text.toString())
        assertTrue(texts(activity).contains("Завтра, 25 нояб."))
        assertTrue(button(activity, "Завтра").isSelected)
        assertTrue(buttons(activity).any { it.text.toString() == "Заодно хлеб" })
        assertEquals(View.VISIBLE, button(activity, "Переместить").visibility)
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun aNewTaskIsWrittenAtTheTopWhenTheScreenIsLeft() {
        val controller = create()
        fields(controller.get())[0].setText("Вынести мусор")
        fields(controller.get())[1].setText("до восьми")
        button(controller.get(), "Через неделю").performClick()
        controller.pause()
        assertEquals("# Дом\n- [ ] Вынести мусор 📅 2026-12-01\n\tдо восьми\n- [ ] Купить молоко", Fixtures.read(context, "Дом").substringBefore(" 📅 2026-11-25"))
    }

    @Test fun aNewTaskWithoutATitleIsNotWritten() {
        val controller = create()
        button(controller.get(), "Сегодня").performClick()
        controller.pause()
        assertEquals(home, Fixtures.read(context, "Дом"))
    }

    @Test fun editsChangeOnlyTheTasksLines() {
        val controller = edit(milk)
        fields(controller.get())[0].setText("Купить кефир")
        fields(controller.get())[1].setText("1%\nбез сахара")
        controller.pause()
        assertEquals(home.replace("Купить молоко", "Купить кефир").replace("\t2,5%\n", "\t1%\n\tбез сахара\n"), Fixtures.read(context, "Дом"))
    }

    @Test fun anEmptyTitleDoesNotReplaceTheOldOne() {
        val controller = edit(milk)
        fields(controller.get())[0].setText("  ")
        controller.pause()
        assertEquals(home, Fixtures.read(context, "Дом"))
    }

    @Test fun theDueDateIsSetAndRemovedByButtons() {
        val activity = edit(milk).get()
        button(activity, "Сегодня").performClick()
        assertTrue("- [ ] Купить молоко 📅 2026-11-24\n" in Fixtures.read(context, "Дом"))
        assertTrue(texts(activity).contains("Сегодня, 24 нояб."))
        button(activity, "Убрать").performClick()
        assertTrue("- [ ] Купить молоко\n" in Fixtures.read(context, "Дом"))
        assertTrue(texts(activity).contains("Без срока"))
    }

    @Test fun anotherDateComesFromTheCalendar() {
        val activity = edit(milk).get()
        button(activity, "Другая дата…").performClick()
        val asked = shadowOf(activity).nextStartedActivityForResult
        assertEquals(InkDateActivity::class.java.name, asked.intent.component?.className)
        val picker = Robolectric.buildActivity(InkDateActivity::class.java, asked.intent).setup().get()
        assertTrue(contentRoot(picker).find<InkTextView>().any { it.text.toString() == "Срок" })
        assertEquals(listOf("24"), contentRoot(picker).find<DayCell>().filter { it.isToday }.map { it.text.toString() })
        shadowOf(activity).receiveResult(asked.intent, Activity.RESULT_OK, Intent().putExtra("date", LocalDate.of(2027, 1, 5).toEpochDay()))
        assertTrue("- [ ] Купить молоко 📅 2027-01-05\n" in Fixtures.read(context, "Дом"))
    }

    @Test fun markingATaskDoneReturnsToTheList() {
        val activity = edit(milk).get()
        button(activity, "Отметить выполненной").performClick()
        assertTrue("- [x] Купить молоко 📅 2026-11-25 ✅ 2026-11-24\n" in Fixtures.read(context, "Дом"))
        assertTrue(activity.isFinishing)
    }

    @Test fun deletingAsksForASecondTapAndTakesTheSubtasks() {
        val activity = edit(milk).get()
        val delete = button(activity, "Удалить")
        delete.performClick()
        assertEquals(home, Fixtures.read(context, "Дом"))
        assertEquals("Удалить?", delete.text.toString())
        delete.performClick()
        assertEquals("# Дом\n- [ ] Полить цветы\n- [x] Позвонить маме ✅ 2026-11-23\n", Fixtures.read(context, "Дом"))
        assertTrue(activity.isFinishing)
    }

    @Test fun aSubtaskIsAddedToItsTask() {
        val activity = edit(milk).get()
        button(activity, "Добавить подзадачу").performClick()
        val sub = Robolectric.buildActivity(TaskActivity::class.java, shadowOf(activity).nextStartedActivity).setup()
        assertEquals(View.GONE, (button(sub.get(), "Добавить подзадачу").parent as View).visibility)
        fields(sub.get())[0].setText("Сыр")
        sub.pause()
        assertTrue("\t- [ ] Заодно хлеб\n\t- [ ] Сыр\n- [ ] Полить цветы" in Fixtures.read(context, "Дом"))
    }

    @Test fun moveHandsTheTaskToTheList() {
        val activity = edit(milk).get()
        button(activity, "Переместить").performClick()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertEquals(milk.line, started.getStringExtra("move_line"))
        assertTrue(activity.isFinishing)
    }

    @Test fun aTaskCannotMoveWhenTheListIsSortedByDateOrTheTaskIsDone() {
        TaskLists.of(context).setSort("Дом", TaskLists.Sort.DUE)
        assertEquals(View.GONE, button(edit(milk).get(), "Переместить").visibility)
        TaskLists.of(context).setSort("Дом", TaskLists.Sort.MANUAL)
        assertEquals(View.GONE, button(edit(TaskRef("- [x] Позвонить маме ✅ 2026-11-23", 0)).get(), "Переместить").visibility)
    }

    @Test fun aTaskDeletedElsewhereClosesTheScreen() {
        val controller = edit(milk)
        controller.pause().stop()
        Fixtures.write(context, "Дом", "- [ ] Другое\n")
        controller.restart().resume()
        assertTrue(controller.get().isFinishing)
        assertEquals("- [ ] Другое\n", Fixtures.read(context, "Дом"))
    }

    @Test fun aTitleEditedHereKeepsItsTaskFoundForLaterEdits() {
        val controller = edit(milk)
        fields(controller.get())[0].setText("Купить кефир")
        controller.pause().resume()
        button(controller.get(), "Сегодня").performClick()
        assertTrue("- [ ] Купить кефир 📅 2026-11-24\n" in Fixtures.read(context, "Дом"))
        assertFalse(controller.get().isFinishing)
    }

    @Test fun aTaskWithoutADueDateOffersNoReminder() {
        val activity = edit(TaskRef("- [ ] Полить цветы", 0)).get()
        assertFalse(button(activity, "Задать время…").isShown)
    }

    @Test fun aTimeSetsAReminderOnTheDueDateAndIsRemoved() {
        val activity = edit(milk).get()
        assertTrue(texts(activity).contains("Без напоминания"))
        assertFalse(button(activity, "Убрать время").isShown)
        pickTime(activity, "Задать время…", LocalTime.of(10, 0))
        assertTrue("- [ ] Купить молоко ⏰ 2026-11-25 10:00 📅 2026-11-25\n" in Fixtures.read(context, "Дом"))
        assertTrue(texts(activity).contains("В 10:00"))
        assertTrue(button(activity, "Убрать время").isShown)
        button(activity, "Убрать время").performClick()
        assertEquals(home, Fixtures.read(context, "Дом"))
    }

    @Test fun aNewTimeKeepsTheReminderOnItsOwnDay() {
        val line = "- [ ] Заплатить ⏰ 2026-11-24 20:00 📅 2026-11-25"
        Fixtures.write(context, "Дом", "$line\n")
        val activity = edit(TaskRef(line, 0)).get()
        assertTrue(texts(activity).contains("Сегодня, 24 нояб., 20:00"))
        pickTime(activity, "Изменить время…", LocalTime.of(21, 0))
        assertEquals("- [ ] Заплатить ⏰ 2026-11-24 21:00 📅 2026-11-25\n", Fixtures.read(context, "Дом"))
    }

    @Test fun aTurnedOffRemindersChannelIsSaidUnderTheReminder() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        ReminderNotifications.createChannel(context)
        manager.createNotificationChannel(manager.getNotificationChannel("reminders").apply { importance = NotificationManager.IMPORTANCE_NONE })
        val activity = edit(milk).get()
        pickTime(activity, "Задать время…", LocalTime.of(10, 0))
        assertTrue(contentRoot(activity).find<InkTextView>().single { it.text.startsWith("Уведомления выключены") }.isShown)
    }

    @Test fun theFirstReminderAsksForNotifications() {
        val activity = edit(milk).get()
        pickTime(activity, "Задать время…", LocalTime.of(10, 0))
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, shadowOf(activity).lastRequestedPermission.requestedPermissions.single())
    }

    @Test fun notificationsTurnedOffAreSaidUnderTheReminderWithAWayToSettings() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        val activity = edit(milk).get()
        val notice = contentRoot(activity).find<InkTextView>().single { it.text.startsWith("Уведомления выключены") }
        assertFalse(notice.isShown)
        pickTime(activity, "Задать время…", LocalTime.of(10, 0))
        assertTrue(notice.isShown)
        button(activity, "Открыть настройки").performClick()
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, shadowOf(activity).nextStartedActivity.action)
    }

    @Test fun aNewTaskIsWrittenWithItsReminder() {
        val controller = create()
        fields(controller.get())[0].setText("Вынести мусор")
        button(controller.get(), "Завтра").performClick()
        pickTime(controller.get(), "Задать время…", LocalTime.of(8, 15))
        controller.pause()
        assertTrue(Fixtures.read(context, "Дом").startsWith("# Дом\n- [ ] Вынести мусор ⏰ 2026-11-25 08:15 📅 2026-11-25\n"))
    }

    private fun pickTime(activity: TaskActivity, label: String, time: LocalTime) {
        button(activity, label).performClick()
        val asked = shadowOf(activity).nextStartedActivityForResult
        assertEquals(InkTimeActivity::class.java.name, asked.intent.component?.className)
        shadowOf(activity).receiveResult(asked.intent, Activity.RESULT_OK, Intent().putExtra("time", time.toSecondOfDay()))
    }

    private fun edit(ref: TaskRef): ActivityController<TaskActivity> =
        Robolectric.buildActivity(TaskActivity::class.java, TaskActivity.edit(context, "Дом", ref)).setup()

    private fun create(): ActivityController<TaskActivity> =
        Robolectric.buildActivity(TaskActivity::class.java, TaskActivity.create(context, "Дом")).setup()

    private fun fields(activity: TaskActivity) = contentRoot(activity).find<InkEditText>()

    private fun buttons(activity: TaskActivity) = contentRoot(activity).find<InkButton>()

    private fun button(activity: TaskActivity, label: String) = buttons(activity).single { it.text.toString() == label }

    private fun texts(activity: TaskActivity) = contentRoot(activity).find<InkTextView>().map { it.text.toString() }
}
