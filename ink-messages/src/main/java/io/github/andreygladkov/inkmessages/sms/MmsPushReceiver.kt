package io.github.andreygladkov.inkmessages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Declared because the SMS role requires a WAP push receiver; downloading MMS is not implemented yet. */
class MmsPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
