package io.github.andreygladkov.inkgallery

import android.app.Application
import io.github.andreygladkov.ink.eink.StemFit
import io.github.andreygladkov.inkgallery.media.Thumbnails
import io.github.andreygladkov.inkgallery.viewer.PanelMode

class InkGalleryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StemFit.attach(this)
        PanelMode.attach(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= TRIM_MEMORY_BACKGROUND) Thumbnails.clear()
    }
}
