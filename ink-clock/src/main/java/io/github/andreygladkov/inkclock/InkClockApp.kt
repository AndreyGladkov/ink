package io.github.andreygladkov.inkclock

import android.app.Application
import io.github.andreygladkov.ink.eink.StemFit
import io.github.andreygladkov.inkclock.notify.ClockNotifications

class InkClockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StemFit.attach(this)
        ClockNotifications.createChannels(this)
    }
}
