package io.github.andreygladkov.inkcalculator

import android.app.Application
import io.github.andreygladkov.ink.eink.StemFit

class InkCalculatorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StemFit.attach(this)
    }
}
