package io.github.andreygladkov.inkclock.desk

import android.service.dreams.DreamService

class ClockDream : DreamService() {
    private lateinit var desk: DeskView

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        desk = DeskView(this)
        setContentView(desk)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        desk.start()
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        desk.stop()
    }
}
