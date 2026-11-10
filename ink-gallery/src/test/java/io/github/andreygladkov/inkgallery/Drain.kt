package io.github.andreygladkov.inkgallery

import android.os.Looper
import org.robolectric.Shadows.shadowOf

/**
 * Waits for every decode and query a test started. One still running when the next test resets the sandbox, or
 * when the test JVM exits, aborts the whole process inside Robolectric's native image decoder.
 */
fun drainBackground() {
    val executors = listOf(Background.queries, Background.thumbnails, Background.frames)
    repeat(3) {
        executors.forEach { it.submit {}.get() }
        shadowOf(Looper.getMainLooper()).idle()
    }
}
