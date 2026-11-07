package io.github.andreygladkov.inklauncher.notify

import android.app.Notification
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class InkNotificationListener : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        NotificationFeed.connect(this)
        republish()
    }

    override fun onListenerDisconnected() {
        main.post { NotificationFeed.disconnect(this) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) = republish()

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) = republish()

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) = republish()

    private fun republish() {
        val active = runCatching { activeNotifications?.toList().orEmpty() }.getOrDefault(emptyList())
        val ranking = runCatching { currentRanking }.getOrNull()
        val notifications = active.map { it.toInk(ranking) }
        main.post { NotificationFeed.publish(notifications) }
    }

    private fun StatusBarNotification.toInk(rankingMap: RankingMap?): InkNotification {
        val extras = notification.extras
        val ranking = Ranking()
        val silent = rankingMap?.getRanking(key, ranking) == true && (ranking.isAmbient || ranking.importance < NotificationManager.IMPORTANCE_DEFAULT)
        return InkNotification(
            key = key,
            packageName = packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty(),
            postedAt = postTime,
            isSilent = silent,
            isClearable = isClearable,
            isAutoCancel = notification.flags and Notification.FLAG_AUTO_CANCEL != 0,
            isOngoing = isOngoing,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            group = if (isGroup) groupKey else null,
            contentIntent = notification.contentIntent,
        )
    }
}
