package io.github.andreygladkov.inkclock.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.andreygladkov.inkclock.notify.ClockNotifications
import io.github.andreygladkov.inkclock.ring.Ring
import io.github.andreygladkov.inkclock.ring.RingService
import java.time.Instant

object TimerActions {
    private const val LATE_TOLERANCE_MS = 1000L

    fun start(context: Context, durationMs: Long, label: String = "", remember: Boolean = true, now: Long = now()) {
        val store = TimerStore.of(context)
        if (remember) store.remember(durationMs)
        apply(context, Timer.start(durationMs, now, label))
    }

    fun pause(context: Context, now: Long = now()) {
        (TimerStore.of(context).timer() as? Timer.Running)?.let { apply(context, it.pause(now)) }
    }

    fun resume(context: Context, now: Long = now()) {
        (TimerStore.of(context).timer() as? Timer.Paused)?.let { apply(context, it.resume(now)) }
    }

    fun addMinute(context: Context) {
        when (val timer = TimerStore.of(context).timer()) {
            is Timer.Running -> apply(context, timer.plus(MINUTE_MS))
            is Timer.Paused -> apply(context, timer.plus(MINUTE_MS))
            Timer.Idle -> Unit
        }
    }

    fun reset(context: Context) = apply(context, Timer.Idle)

    fun reschedule(context: Context) = schedule(context, TimerStore.of(context).timer())

    /** Called when the system alarm goes off; an alarm left over from a timer that was since changed rings nothing. */
    fun fired(context: Context, now: Long = now()): Ring.OfTimer? {
        val timer = TimerStore.of(context).timer() as? Timer.Running ?: return null
        if (timer.remaining(now) > LATE_TOLERANCE_MS) {
            schedule(context, timer)
            return null
        }
        apply(context, Timer.Idle)
        return Ring.OfTimer(timer.durationMs, timer.label)
    }

    private fun apply(context: Context, timer: Timer) {
        TimerStore.of(context).save(timer)
        schedule(context, timer)
        ClockNotifications.showTimer(context, timer)
    }

    private fun schedule(context: Context, timer: Timer) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val fire = PendingIntent.getBroadcast(context, 0, Intent(context, FireReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
        if (timer is Timer.Running) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endsAt, fire) else manager.cancel(fire)
    }

    private fun now() = Instant.now().toEpochMilli()

    private const val MINUTE_MS = 60_000L

    class FireReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = RingService.startTimer(context)
    }
}
