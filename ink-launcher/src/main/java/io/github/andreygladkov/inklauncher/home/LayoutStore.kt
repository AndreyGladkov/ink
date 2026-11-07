package io.github.andreygladkov.inklauncher.home

import android.content.Context
import android.util.AtomicFile
import java.io.File

class LayoutStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, FILE_NAME))

    private val broken = File(context.filesDir, BROKEN_NAME)

    /**
     * Null when there is no layout yet or it cannot be read. An unreadable file is copied aside first, so the
     * layout saved after it does not take the user's tiles with it.
     */
    fun load(): HomeLayout? {
        if (!file.baseFile.exists()) return null
        val bytes = runCatching { file.readFully() }.getOrNull() ?: return null
        return runCatching { LayoutJson.read(bytes.decodeToString()) }.getOrElse {
            if (!broken.exists()) broken.writeBytes(bytes)
            null
        }
    }

    fun save(layout: HomeLayout) {
        val stream = file.startWrite()
        try {
            stream.write(LayoutJson.write(layout).encodeToByteArray())
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    fun update(change: (HomeLayout) -> HomeLayout): HomeLayout = change(load() ?: HomeLayout()).also(::save)

    private companion object {
        const val FILE_NAME = "home.json"
        const val BROKEN_NAME = "home.broken.json"
    }
}
