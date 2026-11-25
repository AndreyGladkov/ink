package io.github.andreygladkov.inktasks

import android.app.Application
import io.github.andreygladkov.ink.eink.StemFit
import io.github.andreygladkov.inktasks.remind.ReminderNotifications

class InkTasksApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StemFit.attach(this)
        ReminderNotifications.createChannel(this)
    }
}
