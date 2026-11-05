package io.github.andreygladkov.inkclock.alarm

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.andreygladkov.inkclock.ClockPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

class AlarmStore(private val prefs: SharedPreferences) {
    fun all(): List<Alarm> {
        val json = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { decode(array.getJSONObject(it)) }.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
    }

    fun get(id: Int): Alarm? = all().firstOrNull { it.id == id }

    fun nextId(): Int = (all().maxOfOrNull { it.id } ?: 0) + 1

    fun save(alarm: Alarm) = write(all().filter { it.id != alarm.id } + alarm)

    fun delete(id: Int) = write(all().filter { it.id != id })

    private fun write(alarms: List<Alarm>) {
        val array = JSONArray()
        alarms.forEach { array.put(encode(it)) }
        prefs.edit(commit = true) { putString(KEY_ALARMS, array.toString()) }
    }

    private fun encode(alarm: Alarm) = JSONObject().apply {
        put("id", alarm.id)
        put("hour", alarm.hour)
        put("minute", alarm.minute)
        put("days", JSONArray(alarm.days.sorted().map { it.value }))
        put("label", alarm.label)
        put("enabled", alarm.isEnabled)
        when (val ringtone = alarm.ringtone) {
            Ringtone.Default -> Unit
            Ringtone.Silent -> put("silent", true)
            is Ringtone.Sound -> put("ringtone", ringtone.uri)
        }
        put("vibrates", alarm.vibrates)
        alarm.snoozedUntil?.let { put("snoozedUntil", it) }
    }

    private fun decode(json: JSONObject): Alarm {
        val days = json.getJSONArray("days")
        return Alarm(
            id = json.getInt("id"),
            hour = json.getInt("hour"),
            minute = json.getInt("minute"),
            days = (0 until days.length()).map { DayOfWeek.of(days.getInt(it)) }.toSet(),
            label = json.optString("label"),
            isEnabled = json.getBoolean("enabled"),
            ringtone = when {
                json.optBoolean("silent") -> Ringtone.Silent
                json.has("ringtone") -> Ringtone.Sound(json.getString("ringtone"))
                else -> Ringtone.Default
            },
            vibrates = json.optBoolean("vibrates", true),
            snoozedUntil = if (json.has("snoozedUntil")) json.getLong("snoozedUntil") else null,
        )
    }

    companion object {
        private const val PREFS = "alarms"
        private const val KEY_ALARMS = "alarms"

        fun of(context: Context) = AlarmStore(ClockPrefs.deviceStorage(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
