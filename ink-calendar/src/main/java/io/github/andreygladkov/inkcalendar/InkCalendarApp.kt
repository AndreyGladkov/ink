package io.github.andreygladkov.inkcalendar

import android.app.Application
import io.github.andreygladkov.ink.eink.StemFit
import io.github.andreygladkov.inkcalendar.remind.ReminderNotifications

class InkCalendarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StemFit.attach(this)
        ReminderNotifications.createChannel(this)
    }
}
