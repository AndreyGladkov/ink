package io.github.andreygladkov.inkgallery.media

import java.util.Locale

/** "1:05" or "1:02:05", the way players show a running time. */
fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    val (hours, minutes, rest) = Triple(seconds / 3600, seconds / 60 % 60, seconds % 60)
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
    else String.format(Locale.ROOT, "%d:%02d", minutes, rest)
}
