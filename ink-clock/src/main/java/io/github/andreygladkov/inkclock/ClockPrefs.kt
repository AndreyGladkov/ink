package io.github.andreygladkov.inkclock

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object ClockPrefs {
    val SNOOZE_CHOICES = listOf(5, 10, 15, 20)
    val RING_CHOICES = listOf(5, 10, 20, 30)
    private const val PREFS = "clock"
    private const val PREF_SNOOZE_MINUTES = "snooze_minutes"
    private const val PREF_RING_MINUTES = "ring_minutes"
    private const val DEFAULT_SNOOZE_MINUTES = 10
    private const val DEFAULT_RING_MINUTES = 10

    /** Alarms ring after a reboot before the first unlock, so everything a ring reads lives in device-protected storage. */
    fun deviceStorage(context: Context): Context = context.createDeviceProtectedStorageContext()

    fun snoozeMinutes(context: Context) = prefs(context).getInt(PREF_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES)

    fun setSnoozeMinutes(context: Context, minutes: Int) = prefs(context).edit { putInt(PREF_SNOOZE_MINUTES, minutes) }

    fun ringMinutes(context: Context) = prefs(context).getInt(PREF_RING_MINUTES, DEFAULT_RING_MINUTES)

    fun setRingMinutes(context: Context, minutes: Int) = prefs(context).edit { putInt(PREF_RING_MINUTES, minutes) }

    private fun prefs(context: Context): SharedPreferences = deviceStorage(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
