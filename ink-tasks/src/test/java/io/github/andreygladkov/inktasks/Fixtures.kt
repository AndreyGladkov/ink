package io.github.andreygladkov.inktasks

import android.content.Context
import io.github.andreygladkov.inktasks.store.Storage
import io.github.andreygladkov.inktasks.store.TaskLists
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

object Fixtures {
    val TODAY: LocalDate = LocalDate.of(2026, 11, 24)

    fun fixToday() {
        TaskLists.clock = Clock.fixed(TODAY.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    }

    fun restoreClock() {
        TaskLists.clock = Clock.systemDefaultZone()
    }

    fun file(context: Context, name: String) = File(File(context.filesDir, Storage.DIR), "$name.md")

    fun write(context: Context, name: String, text: String) {
        file(context, name).apply { parentFile!!.mkdirs() }.writeText(text)
    }

    fun read(context: Context, name: String): String = file(context, name).readText()
}
