package io.github.andreygladkov.inkclock.ring

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.andreygladkov.inkclock.ClockPrefs
import io.github.andreygladkov.inkclock.alarm.AlarmActions
import io.github.andreygladkov.inkclock.notify.ClockNotifications
import io.github.andreygladkov.inkclock.timer.TimerActions

class RingService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val sound by lazy { RingSound(this) }
    private var ring: Ring? = null
    private val giveUp = Runnable { ring?.let { finish(it, Outcome.MISSED) } }

    private enum class Outcome { SNOOZED, DISMISSED, MISSED, EXTENDED }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ring?.let { finish(it, Outcome.MISSED, stopService = false) }
        val started = intent?.let(::resolve)
        if (started == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = ClockNotifications.ringing(this, started)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ClockNotifications.ID_RINGING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(ClockNotifications.ID_RINGING, notification)
        }
        ring = started
        instance = this
        sound.start(started.ringtone, started.vibrates)
        main.postDelayed(giveUp, ClockPrefs.ringMinutes(this) * MINUTE_MS)
        notifyListeners()
        return START_NOT_STICKY
    }

    private fun resolve(intent: Intent): Ring? = when {
        intent.hasExtra(EXTRA_ALARM_ID) -> AlarmActions.fired(this, intent.getIntExtra(EXTRA_ALARM_ID, -1))?.let(Ring::OfAlarm)
        intent.getBooleanExtra(EXTRA_TIMER, false) -> TimerActions.fired(this)
        else -> null
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(giveUp)
        sound.stop()
        if (instance === this) instance = null
        notifyListeners()
    }

    private fun finish(ringing: Ring, outcome: Outcome, stopService: Boolean = true) {
        main.removeCallbacks(giveUp)
        sound.stop()
        ring = null
        when (ringing) {
            is Ring.OfAlarm -> finishAlarm(ringing, outcome)
            is Ring.OfTimer -> finishTimer(ringing, outcome)
        }
        if (stopService) {
            instance = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        notifyListeners()
    }

    private fun finishAlarm(ringing: Ring.OfAlarm, outcome: Outcome) {
        when (outcome) {
            Outcome.SNOOZED -> AlarmActions.snooze(this, ringing.alarm)
            Outcome.DISMISSED, Outcome.EXTENDED -> AlarmActions.dismiss(this, ringing.alarm.id)
            Outcome.MISSED -> {
                AlarmActions.dismiss(this, ringing.alarm.id)
                ClockNotifications.showMissed(this, ringing.alarm)
            }
        }
    }

    private fun finishTimer(ringing: Ring.OfTimer, outcome: Outcome) {
        when (outcome) {
            Outcome.EXTENDED -> TimerActions.start(this, MINUTE_MS, ringing.label, remember = false)
            Outcome.MISSED -> ClockNotifications.showTimerMissed(this, ringing)
            Outcome.SNOOZED, Outcome.DISMISSED -> Unit
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_TIMER = "timer"
        private const val MINUTE_MS = 60_000L
        private var instance: RingService? = null
        private val listeners = mutableSetOf<() -> Unit>()

        val ringing: Ring? get() = instance?.ring

        fun startAlarm(context: Context, alarmId: Int) {
            context.startForegroundService(Intent(context, RingService::class.java).putExtra(EXTRA_ALARM_ID, alarmId))
        }

        fun startTimer(context: Context) {
            context.startForegroundService(Intent(context, RingService::class.java).putExtra(EXTRA_TIMER, true))
        }

        fun snooze(alarmId: Int) = actOnAlarm(alarmId, Outcome.SNOOZED)

        fun dismiss(alarmId: Int) = actOnAlarm(alarmId, Outcome.DISMISSED)

        fun stopTimer() = actOnTimer(Outcome.DISMISSED)

        fun addMinute() = actOnTimer(Outcome.EXTENDED)

        private fun actOnAlarm(alarmId: Int, outcome: Outcome) {
            val service = instance ?: return
            val ring = (service.ring as? Ring.OfAlarm)?.takeIf { it.alarm.id == alarmId } ?: return
            service.finish(ring, outcome)
        }

        private fun actOnTimer(outcome: Outcome) {
            val service = instance ?: return
            val ring = service.ring as? Ring.OfTimer ?: return
            service.finish(ring, outcome)
        }

        fun addListener(listener: () -> Unit) {
            listeners += listener
        }

        fun removeListener(listener: () -> Unit) {
            listeners -= listener
        }

        private fun notifyListeners() = listeners.toList().forEach { it() }
    }
}
