package io.github.andreygladkov.inkphone

import android.app.Application
import io.github.andreygladkov.ink.eink.StemFit

class InkPhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StemFit.attach(this)
    }
}
