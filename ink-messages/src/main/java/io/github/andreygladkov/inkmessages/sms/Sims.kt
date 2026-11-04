package io.github.andreygladkov.inkmessages.sms

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.edit

data class Sim(val subId: Int, val slot: Int, val label: String)

class Sims(private val context: Context) {
    @SuppressLint("MissingPermission")
    fun active(): List<Sim> {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return manager.activeSubscriptionInfoList.orEmpty()
            .sortedBy { it.simSlotIndex }
            .map { Sim(it.subscriptionId, it.simSlotIndex, it.displayName?.toString().orEmpty()) }
    }

    fun defaultSubId(): Int = SmsManager.getDefaultSmsSubscriptionId()

    fun forThread(prefs: SharedPreferences, threadId: Long, sims: List<Sim>): Sim? {
        val remembered = prefs.getInt(key(threadId), SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        return sims.find { it.subId == remembered } ?: sims.find { it.subId == defaultSubId() } ?: sims.firstOrNull()
    }

    fun remember(prefs: SharedPreferences, threadId: Long, sim: Sim) = prefs.edit { putInt(key(threadId), sim.subId) }

    private fun key(threadId: Long) = "sim_$threadId"
}
