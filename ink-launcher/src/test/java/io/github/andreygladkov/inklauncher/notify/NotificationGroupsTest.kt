package io.github.andreygladkov.inklauncher.notify

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationGroupsTest {
    private fun note(key: String, pkg: String, at: Long, silent: Boolean = false, ongoing: Boolean = false, summary: Boolean = false, group: String? = null) =
        InkNotification(key, pkg, key, "", at, isSilent = silent, isOngoing = ongoing, isGroupSummary = summary, group = group)

    @Test fun groupsByAppWithTheNewestFirstEverywhere() {
        val groups = NotificationGroups.of(listOf(
            note("mail1", "mail", 10),
            note("chat1", "chat", 20),
            note("mail2", "mail", 30),
        ))
        assertEquals(listOf("mail", "chat"), groups.map { it.packageName })
        assertEquals(listOf("mail2", "mail1"), groups.first().notifications.map { it.key })
    }

    @Test fun ongoingNotificationsAreLeftOut() {
        val groups = NotificationGroups.of(listOf(note("player", "music", 10, ongoing = true), note("mail", "mail", 5)))
        assertEquals(listOf("mail"), groups.flatMap { it.notifications }.map { it.key })
    }

    @Test fun aSummaryShowsOnlyWithoutItsChildren() {
        val withChildren = listOf(note("sum", "chat", 30, summary = true, group = "g"), note("m1", "chat", 20, group = "g"))
        assertEquals(listOf("m1"), NotificationGroups.shown(withChildren).map { it.key })
        val alone = listOf(note("sum", "chat", 30, summary = true, group = "g"))
        assertEquals(listOf("sum"), NotificationGroups.shown(alone).map { it.key })
    }

    @Test fun silentOnesAreShownButNotCounted() {
        val all = listOf(note("a", "x", 1), note("b", "x", 2, silent = true), note("c", "y", 3, ongoing = true))
        assertEquals(2, NotificationGroups.shown(all).size)
        assertEquals(1, NotificationGroups.loudCount(all))
    }
}
