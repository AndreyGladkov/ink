package io.github.andreygladkov.inkcalendar

import android.app.Application
import io.github.andreygladkov.ink.eink.StemFit

class InkCalendarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StemFit.attach(this)
    }
}
