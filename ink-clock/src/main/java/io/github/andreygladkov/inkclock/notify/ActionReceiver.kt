package io.github.andreygladkov.inkclock.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.andreygladkov.inkclock.alarm.AlarmActions
import io.github.andreygladkov.inkclock.pomodoro.PomodoroActions
import io.github.andreygladkov.inkclock.ring.RingService
import io.github.andreygladkov.inkclock.timer.TimerActions

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        when (intent.action) {
            ACTION_SNOOZE -> RingService.snooze(alarmId)
            ACTION_DISMISS -> RingService.dismiss(alarmId)
            ACTION_CANCEL_SNOOZE -> AlarmActions.dismiss(context, alarmId)
            ACTION_TIMER_STOP -> RingService.stopTimer()
            ACTION_TIMER_ADD_MINUTE -> RingService.addMinute()
            ACTION_TIMER_PAUSE -> TimerActions.pause(context)
            ACTION_TIMER_RESUME -> TimerActions.resume(context)
            ACTION_TIMER_RESET -> TimerActions.reset(context)
            ACTION_POMODORO_START -> PomodoroActions.start(context)
            ACTION_POMODORO_STOP -> PomodoroActions.stop(context)
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        private const val PREFIX = "io.github.andreygladkov.inkclock."
        const val ACTION_SNOOZE = PREFIX + "SNOOZE"
        const val ACTION_DISMISS = PREFIX + "DISMISS"
        const val ACTION_CANCEL_SNOOZE = PREFIX + "CANCEL_SNOOZE"
        const val ACTION_TIMER_STOP = PREFIX + "TIMER_STOP"
        const val ACTION_TIMER_ADD_MINUTE = PREFIX + "TIMER_ADD_MINUTE"
        const val ACTION_TIMER_PAUSE = PREFIX + "TIMER_PAUSE"
        const val ACTION_TIMER_RESUME = PREFIX + "TIMER_RESUME"
        const val ACTION_TIMER_RESET = PREFIX + "TIMER_RESET"
        const val ACTION_POMODORO_START = PREFIX + "POMODORO_START"
        const val ACTION_POMODORO_STOP = PREFIX + "POMODORO_STOP"
    }
}
