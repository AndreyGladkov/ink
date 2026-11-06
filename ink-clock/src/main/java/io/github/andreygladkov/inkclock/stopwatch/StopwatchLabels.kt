package io.github.andreygladkov.inkclock.stopwatch

object StopwatchLabels {
    fun elapsed(ms: Long, tenths: Boolean): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val base = if (hours > 0) "%d:%02d:%02d".format(hours, totalSeconds / 60 % 60, totalSeconds % 60)
        else "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
        return if (tenths) "$base.${ms % 1000 / 100}" else base
    }
}
