package io.github.andreygladkov.inkclock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import io.github.andreygladkov.inkclock.alarm.AlarmActions
import io.github.andreygladkov.inkclock.alarm.AlarmEditActivity
import io.github.andreygladkov.inkclock.alarm.AlarmLabels
import io.github.andreygladkov.inkclock.alarm.AlarmRequest
import io.github.andreygladkov.inkclock.alarm.AlarmStore
import io.github.andreygladkov.inkclock.ring.Ring
import io.github.andreygladkov.inkclock.ring.RingService
import io.github.andreygladkov.inkclock.timer.TimerActions
import io.github.andreygladkov.inkclock.timer.TimerLabels

/** Entry point for other apps' AlarmClock intents; it shows no window of its own. */
class ClockIntentsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ringingAlarm = (RingService.ringing as? Ring.OfAlarm)?.alarm
        when (intent.action) {
            AlarmClock.ACTION_SET_ALARM -> setAlarm(intent)
            AlarmClock.ACTION_SHOW_ALARMS -> startActivity(MainActivity.intent(this, Tab.ALARMS))
            AlarmClock.ACTION_SNOOZE_ALARM -> ringingAlarm?.let { RingService.snooze(it.id) }
            AlarmClock.ACTION_DISMISS_ALARM -> ringingAlarm?.let { RingService.dismiss(it.id) }
            AlarmClock.ACTION_SET_TIMER -> setTimer(intent)
            AlarmClock.ACTION_SHOW_TIMERS -> startActivity(MainActivity.intent(this, Tab.TIMER))
        }
        finish()
    }

    private fun setAlarm(intent: Intent) {
        val request = AlarmRequest.from(intent)
        if (request.hour == null || !request.skipUi) {
            startActivity(AlarmEditActivity.create(this, request.hour, request.minute, request.label))
            return
        }
        val store = AlarmStore.of(this)
        val alarm = request.toAlarm(store.all().firstOrNull { request.matches(it) }?.id ?: store.nextId())
        AlarmActions.save(this, alarm)
        Toast.makeText(this, getString(R.string.alarm_set_for, AlarmLabels(this).time(alarm.hour, alarm.minute)), Toast.LENGTH_SHORT).show()
    }

    private fun setTimer(intent: Intent) {
        val seconds = intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0).takeIf { it in 1..MAX_TIMER_SECONDS }
        if (seconds != null) {
            val durationMs = seconds * 1000L
            TimerActions.start(this, durationMs, intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty())
            if (intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)) {
                Toast.makeText(this, getString(R.string.timer_set_for, TimerLabels(this).duration(durationMs)), Toast.LENGTH_SHORT).show()
                return
            }
        }
        startActivity(MainActivity.intent(this, Tab.TIMER))
    }

    private companion object {
        const val MAX_TIMER_SECONDS = 24 * 60 * 60
    }
}
