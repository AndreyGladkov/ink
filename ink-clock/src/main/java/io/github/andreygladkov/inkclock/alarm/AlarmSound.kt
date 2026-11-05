package io.github.andreygladkov.inkclock.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.net.toUri

class AlarmSound(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).build()
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    private var player: MediaPlayer? = null
    private var tones: ToneGenerator? = null
    private val beep = object : Runnable {
        override fun run() {
            tones?.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_MS)
            main.postDelayed(this, BEEP_PERIOD_MS)
        }
    }
    private var step = 0
    private val raise = object : Runnable {
        override fun run() {
            step++
            val volume = rampVolume(step)
            player?.setVolume(volume, volume)
            if (volume < 1f) main.postDelayed(this, RAMP_STEP_MS)
        }
    }

    fun start(alarm: Alarm) {
        stop()
        audio.requestAudioFocus(focus)
        player = candidates(alarm.ringtone).firstNotNullOfOrNull(::play)
        if (player == null && alarm.ringtone != Ringtone.Silent) startBeeping()
        if (alarm.vibrates) vibrate()
    }

    fun stop() {
        main.removeCallbacks(raise)
        main.removeCallbacks(beep)
        tones?.release()
        tones = null
        player?.run {
            runCatching { stop() }
            release()
        }
        player = null
        vibrator.cancel()
        audio.abandonAudioFocusRequest(focus)
    }

    private fun candidates(ringtone: Ringtone): List<Uri> = when (ringtone) {
        Ringtone.Silent -> emptyList()
        is Ringtone.Sound -> listOf(ringtone.uri.toUri()) + defaults()
        Ringtone.Default -> defaults()
    }

    // The settings URIs, not the media files they point to: before the first unlock the media provider is gone,
    // and MediaPlayer then plays these from the copy the system caches in device-protected storage.
    private fun defaults(): List<Uri> = listOf(Settings.System.DEFAULT_ALARM_ALERT_URI, Settings.System.DEFAULT_RINGTONE_URI)

    private fun play(uri: Uri): MediaPlayer? {
        val created = MediaPlayer()
        return runCatching {
            created.setAudioAttributes(attributes)
            created.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            created.setDataSource(context, uri)
            created.isLooping = true
            created.prepare()
            step = 0
            rampVolume(step).let { created.setVolume(it, it) }
            created.start()
            main.postDelayed(raise, RAMP_STEP_MS)
            created
        }.getOrElse {
            created.release()
            null
        }
    }

    // Before the first unlock the media provider that serves ringtones can be unavailable; a generated tone
    // needs no file, so the alarm is still heard.
    private fun startBeeping() {
        tones = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull() ?: return
        main.post(beep)
    }

    private fun vibrate() {
        val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, attributes)
        }
    }

    companion object {
        private const val RAMP_STEP_MS = 1000L
        private const val RAMP_STEPS = 20
        private const val START_VOLUME = 0.1f
        private const val BEEP_MS = 400
        private const val BEEP_PERIOD_MS = 1000L
        private val VIBRATION_PATTERN = longArrayOf(0, 600, 600)

        fun rampVolume(step: Int): Float = (START_VOLUME + (1f - START_VOLUME) * step / RAMP_STEPS).coerceAtMost(1f)
    }
}
