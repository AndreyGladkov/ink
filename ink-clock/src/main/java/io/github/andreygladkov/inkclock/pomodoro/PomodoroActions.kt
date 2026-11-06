package io.github.andreygladkov.inkclock.pomodoro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.andreygladkov.inkclock.notify.ClockNotifications
import java.time.Instant
import java.time.ZoneId

object PomodoroActions {
    private const val LATE_TOLERANCE_MS = 1000L

    fun start(context: Context, now: Long = now()) = update(context) { it.start(now) }

    fun stop(context: Context) = update(context) { it.stop() }

    fun skipBreak(context: Context) = update(context) { it.skipBreak() }

    /** Ends the phase once its time is up; called both by the system alarm and by the screen, whichever comes first. */
    fun finishIfDue(context: Context, now: Long = now()): Boolean {
        val store = PomodoroStore.of(context)
        val pomodoro = store.pomodoro()
        val plan = store.plan()
        val endsAt = pomodoro.endsAt(plan) ?: return false
        if (endsAt - now > LATE_TOLERANCE_MS) return false
        val finished = pomodoro.finish(plan, today(now))
        apply(context, finished)
        ClockNotifications.showPomodoroDone(context, pomodoro.phase, finished, plan, store.playsSound)
        return true
    }

    fun reschedule(context: Context) {
        if (!finishIfDue(context)) schedule(context, PomodoroStore.of(context).pomodoro())
    }

    private fun update(context: Context, change: (Pomodoro) -> Pomodoro): Pomodoro {
        val changed = change(PomodoroStore.of(context).pomodoro())
        apply(context, changed)
        ClockNotifications.cancelPomodoroDone(context)
        return changed
    }

    private fun apply(context: Context, pomodoro: Pomodoro) {
        val store = PomodoroStore.of(context)
        store.save(pomodoro)
        schedule(context, pomodoro)
        ClockNotifications.showPomodoro(context, pomodoro, store.plan())
    }

    private fun schedule(context: Context, pomodoro: Pomodoro) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val fire = PendingIntent.getBroadcast(context, 0, Intent(context, PhaseEndReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
        val endsAt = pomodoro.endsAt(PomodoroStore.of(context).plan())
        if (endsAt != null) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, fire) else manager.cancel(fire)
    }

    fun today(now: Long = now()): Long = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    private fun now() = Instant.now().toEpochMilli()

    class PhaseEndReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finishIfDue(context)
        }
    }
}
