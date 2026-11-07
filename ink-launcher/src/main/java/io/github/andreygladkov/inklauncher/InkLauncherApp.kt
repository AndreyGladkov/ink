package io.github.andreygladkov.inklauncher

import android.app.Application
import io.github.andreygladkov.ink.eink.StemFit

class InkLauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StemFit.attach(this)
    }
}
