package io.github.andreygladkov.inklauncher.notify

import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationFeedTest {
    private var calls = 0
    private val observer: () -> Unit = { calls++ }

    @After fun clear() {
        NotificationFeed.stopObserving(observer)
        NotificationFeed.publish(emptyList())
    }

    @Test fun observersHearOnlyOfAChangedList() {
        NotificationFeed.observe(observer)
        val mail = InkNotification("1", "com.mail", "Письмо", "", postedAt = 1)
        NotificationFeed.publish(listOf(mail))
        NotificationFeed.publish(listOf(mail))
        NotificationFeed.publish(listOf(mail.copy(text = "Новое")))
        assertEquals(2, calls)
    }
}
