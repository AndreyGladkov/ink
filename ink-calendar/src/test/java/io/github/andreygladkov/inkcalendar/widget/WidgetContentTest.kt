package io.github.andreygladkov.inkcalendar.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.inkcalendar.Fixtures.TODAY
import io.github.andreygladkov.inkcalendar.Fixtures.ZONE
import io.github.andreygladkov.inkcalendar.Fixtures.at
import io.github.andreygladkov.inkcalendar.Fixtures.utcDay
import io.github.andreygladkov.inkcalendar.events.Occurrence
import io.github.andreygladkov.inkcalendar.tasks.DueTask
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class WidgetContentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.ofEpochMilli(at(TODAY, 12))
    private val occurrences = listOf(
        occurrence("Стендап", at(TODAY, 9), at(TODAY, 10)),
        occurrence("Обед", at(TODAY, 11, 30), at(TODAY, 12, 30)),
        occurrence("Ужин", at(TODAY, 19), at(TODAY, 20)),
        occurrence("День рождения", utcDay(TODAY), utcDay(TODAY.plusDays(1)), allDay = true),
        occurrence("Созвон", at(TODAY.plusDays(1), 11), at(TODAY.plusDays(1), 12)),
    )

    @Test fun theDateIsTodaysAndEventsThatEndedAreGone() {
        val content = WidgetContent.of(context, now, ZONE, occurrences)
        assertEquals(listOf("Четверг", "26", "Ноябрь"), listOf(content.weekday, content.day, content.month))
        assertEquals(listOf(WidgetContent.Entry(null, "День рождения"), WidgetContent.Entry("11:30", "Обед"), WidgetContent.Entry("19:00", "Ужин")),
            content.todayEntries)
        assertEquals(listOf(WidgetContent.Entry("11:00", "Созвон")), content.tomorrowEntries)
        assertNull(content.note)
    }

    @Test fun tasksDueTodayAndTomorrowFollowTheEvents() {
        val tasks = listOf(
            DueTask("Дом", "- [ ] Купить молоко", 0, "Купить молоко", TODAY, null),
            DueTask("Работа", "- [ ] Отчёт", 0, "Отчёт", TODAY.plusDays(1), TODAY.plusDays(1).atTime(9, 0)),
            DueTask("Дом", "- [ ] Потом", 0, "Потом", TODAY.plusDays(2), null),
        )
        val content = WidgetContent.of(context, now, ZONE, emptyList(), tasks)
        assertEquals(listOf(WidgetContent.Entry(null, "Купить молоко", isTask = true)), content.todayEntries)
        assertEquals(listOf(WidgetContent.Entry("9:00", "Отчёт", isTask = true)), content.tomorrowEntries)
        assertNull(content.note)
    }

    @Test fun anEmptyDayOrNoAccessIsSaid() {
        assertEquals("Нет событий", WidgetContent.of(context, now, ZONE, emptyList()).note)
        assertEquals("Откройте календарь и разрешите доступ", WidgetContent.of(context, now, ZONE, null).note)
    }

    @Test fun theWidgetChangesNextWhenAShownEventEndsOrAtMidnight() {
        assertEquals(Instant.ofEpochMilli(at(TODAY, 12, 30)), WidgetContent.nextChange(now, ZONE, occurrences))
        assertEquals(TODAY.plusDays(1).atStartOfDay(ZONE).toInstant(), WidgetContent.nextChange(now, ZONE, null))
    }

    private fun occurrence(title: String, begin: Long, end: Long, allDay: Boolean = false) =
        Occurrence(1, begin, end, allDay, title, 1, "", "", "", repeats = false)
}
