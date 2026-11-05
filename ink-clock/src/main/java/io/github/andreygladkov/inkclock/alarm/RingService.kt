package io.github.andreygladkov.inkclock.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.andreygladkov.inkclock.ClockPrefs
import io.github.andreygladkov.inkclock.notify.ClockNotifications

class RingService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val sound by lazy { AlarmSound(this) }
    private var alarm: Alarm? = null
    private val giveUp = Runnable { alarm?.let { finish(it, Outcome.MISSED) } }

    private enum class Outcome { SNOOZED, DISMISSED, MISSED }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1
        alarm?.let { finish(it, Outcome.MISSED, stopService = false) }
        val fired = if (id >= 0) AlarmActions.fired(this, id) else null
        if (fired == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = ClockNotifications.ringing(this, fired)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ClockNotifications.ID_RINGING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(ClockNotifications.ID_RINGING, notification)
        }
        alarm = fired
        instance = this
        sound.start(fired)
        main.postDelayed(giveUp, ClockPrefs.ringMinutes(this) * MINUTE_MS)
        notifyListeners()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(giveUp)
        sound.stop()
        if (instance === this) instance = null
        notifyListeners()
    }

    private fun finish(ringing: Alarm, outcome: Outcome, stopService: Boolean = true) {
        main.removeCallbacks(giveUp)
        sound.stop()
        alarm = null
        when (outcome) {
            Outcome.SNOOZED -> AlarmActions.snooze(this, ringing)
            Outcome.DISMISSED -> AlarmActions.dismiss(this, ringing.id)
            Outcome.MISSED -> {
                AlarmActions.dismiss(this, ringing.id)
                ClockNotifications.showMissed(this, ringing)
            }
        }
        if (stopService) {
            instance = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        notifyListeners()
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        private const val MINUTE_MS = 60_000L
        private var instance: RingService? = null
        private val listeners = mutableSetOf<() -> Unit>()

        val ringing: Alarm? get() = instance?.alarm

        fun start(context: Context, alarmId: Int) {
            context.startForegroundService(Intent(context, RingService::class.java).putExtra(EXTRA_ALARM_ID, alarmId))
        }

        fun snooze(alarmId: Int) = act(alarmId, Outcome.SNOOZED)

        fun dismiss(alarmId: Int) = act(alarmId, Outcome.DISMISSED)

        private fun act(alarmId: Int, outcome: Outcome) {
            val service = instance ?: return
            val alarm = service.alarm?.takeIf { it.id == alarmId } ?: return
            service.finish(alarm, outcome)
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
