package io.github.andreygladkov.inkweather

import android.app.Application
import io.github.andreygladkov.ink.eink.StemFit

class InkWeatherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StemFit.attach(this)
    }
}
