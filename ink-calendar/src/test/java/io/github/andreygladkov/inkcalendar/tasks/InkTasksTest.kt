package io.github.andreygladkov.inkcalendar.tasks

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class InkTasksTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test fun withoutInkTasksOrItsPermissionThereAreNoTasks() {
        assertFalse(InkTasks(application).isInstalled)
        assertEquals(emptyList(), InkTasks(application).due())
        FakeTasksProvider.install(application, granted = false).task("Купить молоко", "2026-11-26")
        assertTrue(InkTasks(application).isInstalled)
        assertEquals(emptyList(), InkTasks(application).due())
    }

    @Test fun dueTasksAreReadUnlessTurnedOffAndUnreadableDatesAreSkipped() {
        FakeTasksProvider.install(application).apply {
            task("Купить молоко", "2026-11-26", reminder = "2026-11-26T09:30")
            task("Сломанная дата", "26.11.2026")
            task("Отчёт", "2026-11-27", list = "Работа", reminder = "2026-11-25T15:00")
        }
        val tasks = InkTasks(application).due()
        assertEquals(listOf("Купить молоко", "Отчёт"), tasks.map { it.title })
        assertEquals(DueTask("Дом", "- [ ] Купить молоко", 0, "Купить молоко", LocalDate.of(2026, 11, 26), LocalDateTime.of(2026, 11, 26, 9, 30)), tasks[0])
        assertEquals(LocalTime.of(9, 30), tasks[0].time)
        assertNull(tasks[1].time)
        InkTasks(application).setOn(false)
        assertEquals(emptyList(), InkTasks(application).due())
    }

    @Test fun aDayListsItsTasksWithATimeFirst() {
        val day = LocalDate.of(2026, 11, 26)
        fun task(title: String, due: LocalDate, at: Int?) = DueTask("Дом", title, 0, title, due, at?.let { due.atTime(it, 0) })
        val tasks = listOf(task("Без времени", day, null), task("Вечером", day, 19), task("Утром", day, 9), task("Завтра", day.plusDays(1), 8))
        assertEquals(listOf("Утром", "Вечером", "Без времени"), DueTask.onDay(tasks, day).map { it.title })
    }
}
