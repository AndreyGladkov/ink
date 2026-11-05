package io.github.andreygladkov.inkclock.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.andreygladkov.inkclock.alarm.AlarmActions
import io.github.andreygladkov.inkclock.alarm.RingService

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        when (intent.action) {
            ACTION_SNOOZE -> RingService.snooze(alarmId)
            ACTION_DISMISS -> RingService.dismiss(alarmId)
            ACTION_CANCEL_SNOOZE -> AlarmActions.dismiss(context, alarmId)
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val ACTION_SNOOZE = "io.github.andreygladkov.inkclock.SNOOZE"
        const val ACTION_DISMISS = "io.github.andreygladkov.inkclock.DISMISS"
        const val ACTION_CANCEL_SNOOZE = "io.github.andreygladkov.inkclock.CANCEL_SNOOZE"
    }
}
