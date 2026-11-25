package io.github.andreygladkov.inktasks.remind

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.inktasks.Fixtures
import io.github.andreygladkov.inktasks.store.Storage
import io.github.andreygladkov.inktasks.store.TaskLists
import io.github.andreygladkov.inktasks.store.TestDocumentsProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class RemindersTest {
    @get:Rule val temp = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
    private val home = """
        # Дом
        - [ ] Полить цветы ⏰ 2026-11-24 11:00
        - [ ] Купить молоко ⏰ 2026-11-24 13:00 📅 2026-11-24
        	- [ ] Заодно хлеб ⏰ 2026-11-24 14:00
        - [x] Позвонить маме ⏰ 2026-11-24 12:30 ✅ 2026-11-23
        - [ ] Починить кран
    """.trimIndent() + "\n"

    @Before fun setUp() {
        Fixtures.fixToday()
        Fixtures.write(context, "Дом", home)
        Fixtures.write(context, "Работа", "- [ ] Отчёт ⏰ 2026-11-24 15:00\n")
        ReminderNotifications.createChannel(context)
    }

    @After fun restore() = Fixtures.restoreClock()

    @Test fun openTasksAndSubtasksWithATimeAreReminders() {
        val reminders = Reminders.inFile("Дом", TaskLists.of(context).read("Дом"))
        assertEquals(listOf("Полить цветы", "Купить молоко", "Заодно хлеб"), reminders.map { it.title })
        assertEquals(LocalDateTime.of(2026, 11, 24, 14, 0), reminders.last().at)
    }

    @Test fun theFirstCheckNotifiesNothingFromBeforeAndWakesForTheNextReminder() {
        Reminders.update(context)
        assertTrue(notifications.allNotifications.isEmpty())
        assertEquals(at(13, 0), nextCheck())
    }

    @Test fun eachCheckNotifiesWhatCameDueSinceTheLastOne() {
        Reminders.update(context)
        moveTo(14, 0)
        check()
        assertEquals(listOf("Купить молоко", "Заодно хлеб"), notifications.allNotifications.map { it.title() })
        val milk = notifications.getNotification("Дом\n- [ ] Купить молоко ⏰ 2026-11-24 13:00 📅 2026-11-24\n0", 1)
        assertEquals("Дом", milk.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(at(15, 0), nextCheck())
        check()
        assertEquals(2, notifications.allNotifications.size)
    }

    @Test fun doneFromTheNotificationChecksTheTaskOffAndTakesTheNotificationBack() {
        Reminders.update(context)
        moveTo(13, 0)
        check()
        press(notifications.allNotifications.single(), "Выполнено")
        assertTrue("- [x] Купить молоко ⏰ 2026-11-24 13:00 📅 2026-11-24 ✅ 2026-11-24\n" in Fixtures.read(context, "Дом"))
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test fun laterMovesTheReminderAnHourOnAndWakesForIt() {
        Reminders.update(context)
        moveTo(13, 0)
        check()
        press(notifications.allNotifications.single(), "Через час")
        assertTrue("- [ ] Купить молоко ⏰ 2026-11-24 14:00 📅 2026-11-24\n" in Fixtures.read(context, "Дом"))
        assertTrue(notifications.allNotifications.isEmpty())
        assertEquals(at(14, 0), nextCheck())
    }

    @Test fun aTaskRenamedInTheAppHasItsNotificationRenamedAndADoneOneTakesItBack() {
        Reminders.update(context)
        moveTo(13, 0)
        check()
        val renamed = TaskLists.of(context).change("Дом") { it.setTitle(it.tasks[1].ref, "Купить кефир") }!!
        assertEquals(listOf("Купить кефир"), notifications.allNotifications.map { it.title() })
        TaskLists.of(context).change("Дом") { it.setDone(renamed.ref!!, true, Fixtures.TODAY) }
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test fun aReminderThatIsAlreadyDueWhenSetComesAtOnce() {
        Reminders.update(context)
        TaskLists.of(context).change("Дом") { it.setReminder(it.tasks.last().ref, LocalDateTime.of(2026, 11, 24, 9, 0)) }
        assertEquals(listOf("Починить кран"), notifications.allNotifications.map { it.title() })
    }

    @Test fun aRenamedListBringsItsDueRemindersBackUnderTheNewName() {
        Reminders.update(context)
        moveTo(13, 0)
        check()
        TaskLists.of(context).rename("Дом", "Home")
        assertTrue("Купить молоко" in notifications.allNotifications.map { it.title() })
        assertEquals(setOf("Home"), notifications.allNotifications.map { it.extras.getCharSequence(Notification.EXTRA_TEXT).toString() }.toSet())
    }

    @Test fun theListsAreReadAgainInTheBackgroundOnlyWhenTheyLiveInAChosenFolder() {
        Reminders.update(context)
        assertNull(scan())
        val storage = Storage(context)
        assertTrue(storage.choose(TestDocumentsProvider.install(temp.root, "Tasks")))
        assertEquals(AlarmManager.INTERVAL_HALF_HOUR, scan()?.interval)
        storage.keepOnPhone()
        assertNull(scan())
    }

    private fun at(hour: Int, minute: Int) = LocalDateTime.of(2026, 11, 24, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun moveTo(hour: Int, minute: Int) {
        TaskLists.fixedClock = Clock.fixed(LocalDateTime.of(2026, 11, 24, hour, minute).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    }

    private fun check() = ReminderReceiver().onReceive(context, Intent(Reminders.ACTION_CHECK))

    private fun nextCheck() = alarms.scheduledAlarms.single { it.type == AlarmManager.RTC_WAKEUP }.triggerAtTime

    private fun scan() = alarms.scheduledAlarms.singleOrNull { it.type == AlarmManager.ELAPSED_REALTIME_WAKEUP }

    private fun press(notification: Notification, label: String) {
        val action = notification.actions.single { it.title.toString() == label }
        ReminderReceiver().onReceive(context, shadowOf(action.actionIntent).savedIntent)
    }

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
}
