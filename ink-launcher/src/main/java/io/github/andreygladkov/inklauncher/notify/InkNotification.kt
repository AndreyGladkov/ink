package io.github.andreygladkov.inklauncher.notify

import android.app.PendingIntent

data class InkNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val isSilent: Boolean = false,
    val isClearable: Boolean = true,
    val isAutoCancel: Boolean = false,
    val isOngoing: Boolean = false,
    val isGroupSummary: Boolean = false,
    val group: String? = null,
    val contentIntent: PendingIntent? = null,
)

data class NotificationGroup(val packageName: String, val notifications: List<InkNotification>)

object NotificationGroups {
    /**
     * Ongoing ones (music, navigation, running services) are left out. A group summary repeats its children,
     * so it shows only when none of them is posted.
     */
    fun shown(all: List<InkNotification>): List<InkNotification> {
        val withChildren = all.filter { !it.isGroupSummary && it.group != null }.map { it.packageName to it.group }.toSet()
        return all.filter { !it.isOngoing && !(it.isGroupSummary && (it.packageName to it.group) in withChildren) }
    }

    fun of(all: List<InkNotification>): List<NotificationGroup> = shown(all)
        .groupBy { it.packageName }
        .map { (pkg, items) -> NotificationGroup(pkg, items.sortedByDescending { it.postedAt }) }
        .sortedByDescending { group -> group.notifications.first().postedAt }

    fun loudCount(all: List<InkNotification>): Int = shown(all).count { !it.isSilent }
}
