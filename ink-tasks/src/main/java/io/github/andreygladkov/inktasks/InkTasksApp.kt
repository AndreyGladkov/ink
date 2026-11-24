package io.github.andreygladkov.inktasks

import android.app.Application
import io.github.andreygladkov.ink.eink.StemFit

class InkTasksApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StemFit.attach(this)
    }
}
