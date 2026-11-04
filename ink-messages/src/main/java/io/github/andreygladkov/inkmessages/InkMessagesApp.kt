package io.github.andreygladkov.inkmessages

import android.app.Application
import io.github.andreygladkov.ink.eink.StemFit

class InkMessagesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StemFit.attach(this)
    }
}
