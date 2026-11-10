package io.github.andreygladkov.inkgallery.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import io.github.andreygladkov.ink.eink.A9Panel
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkButtonRow
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.inkgallery.Background
import io.github.andreygladkov.inkgallery.GalleryPrefs
import io.github.andreygladkov.inkgallery.R
import io.github.andreygladkov.inkgallery.media.Item
import io.github.andreygladkov.inkgallery.media.Library
import io.github.andreygladkov.inkgallery.media.formatDuration
import io.github.andreygladkov.inkgallery.viewer.ActionPanel
import io.github.andreygladkov.inkgallery.viewer.Actions
import io.github.andreygladkov.inkgallery.viewer.InfoActivity
import io.github.andreygladkov.inkgallery.viewer.ModeScreen
import io.github.andreygladkov.inkgallery.viewer.Overlay
import io.github.andreygladkov.inkgallery.viewer.Viewport

/**
 * A video on the whole screen, played with the A9 panel in SPEED, its fastest mode. A tap shows or hides the
 * controls, which never hide by themselves: every redraw shows on e-ink. While they are shown the time and the
 * progress line move once a second. Leaving the app pauses the video, and coming back plays on from there. A video
 * deleted here closes the player with [RESULT_DELETED] and its address as the result's data.
 */
class VideoActivity : Activity(), ModeScreen {
    private val player = MediaPlayer()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var texture: TextureView
    private lateinit var controls: List<View>
    private lateinit var time: InkTextView
    private lateinit var progress: ProgressLine
    private lateinit var playPause: InkButton
    private lateinit var unplayable: InkTextView
    private var isPrepared = false
    private var isReleased = false
    private var isStarted = false
    private var hasEnded = false
    private var wantsToPlay = true
    private var startAt = 0
    private var seekingTo: Int? = null
    private var videoSize: Size? = null
    private var video: Item? = null
    private val uri: Uri? by lazy {
        intent.data.takeIf { intent.action == Intent.ACTION_VIEW } ?: IntentCompat.getParcelableExtra(intent, EXTRA_VIDEO, Uri::class.java)
    }
    private lateinit var itemActions: List<View>
    private lateinit var delete: InkButton
    private val tick = object : Runnable {
        override fun run() {
            showTime()
            if (isPlaying && controls.first().isVisible) main.postDelayed(this, TICK_MS)
        }
    }

    val isPlaying: Boolean get() = isPrepared && player.isPlaying

    val position: Int get() = if (isPrepared) player.currentPosition else startAt

    override fun refreshMode() = A9Panel.RefreshMode.SPEED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            startAt = it.getInt(STATE_POSITION)
            wantsToPlay = it.getBoolean(STATE_PLAYING, true)
        }
        texture = TextureView(this).apply {
            surfaceTextureListener = surfaceListener
            setOnClickListener { toggleControls() }
        }
        time = InkHeader.title(this, "")
        unplayable = InkTextView(this).apply {
            setText(R.string.unplayable)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        progress = ProgressLine(this, ::seekTo).apply { contentDescription = getString(R.string.seek) }
        playPause = Overlay.action(this, R.drawable.ic_play, R.string.play) { togglePlay() }
        val actions = ActionPanel(this)
        itemActions = listOf(
            actions.add(R.drawable.ic_share, R.string.share) { video?.let { Actions.share(this, listOf(it)) } },
            actions.add(R.drawable.ic_info, R.string.info) { video?.let { startActivity(InfoActivity.intent(this, it)) } },
            actions.addDelete({ getString(R.string.delete_video_question) }) { video?.let { Actions.askToDelete(this, listOf(it)) } }.also { delete = it },
        ).onEach { it.isVisible = false }
        val top = Overlay.bar(this, InkHeader.of(this, time, InkHeader.back(this, ::finish)))
        val bottom = Overlay.bar(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkHeader.line(this@VideoActivity))
            addView(progress, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(InkButtonRow(this@VideoActivity, 0, InkStyle.SECONDARY_SCALE).apply {
                addButton(Overlay.action(this@VideoActivity, 0, R.string.back_10) { skip(-SKIP_MS) })
                addButton(playPause)
                addButton(Overlay.action(this@VideoActivity, 0, R.string.forward_10) { skip(SKIP_MS) })
            })
            addView(InkHeader.line(this@VideoActivity))
            addView(actions, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })
        controls = listOf(top, bottom)
        InkWindow.setContent(this, FrameLayout(this).apply {
            setBackgroundColor(InkStyle.of(this@VideoActivity).background)
            addView(texture, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(unplayable, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
            addView(top, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))
            addView(bottom, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        })
        preparePlayer()
        loadItem()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != Actions.REQUEST_DELETE || resultCode != RESULT_OK) return
        setResult(RESULT_DELETED, Intent().setData(uri))
        finish()
    }

    /** What the actions need to know of the video, the same whether it came from the grid or from another app. */
    private fun loadItem() {
        val uri = uri ?: return
        val resolver = contentResolver
        Background.queries.execute {
            val found = runCatching { Library.item(resolver, uri) }.getOrNull() ?: return@execute
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                video = found
                itemActions.forEach { it.isVisible = it != delete || found.isInLibrary }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isStarted = true
        if (isPrepared && wantsToPlay) play()
    }

    override fun onStop() {
        super.onStop()
        isStarted = false
        if (isPlaying) player.pause()
        keepScreenOn(false)
        main.removeCallbacks(tick)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_POSITION, position)
        outState.putBoolean(STATE_PLAYING, wantsToPlay)
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(tick)
        isReleased = true
        player.release()
    }

    private fun preparePlayer() {
        player.setOnPreparedListener {
            isPrepared = true
            player.seekTo(startAt)
            if (wantsToPlay && isStarted) play() else showTime()
        }
        player.setOnCompletionListener {
            hasEnded = true
            wantsToPlay = false
            keepScreenOn(false)
            showPlayPause()
            controls.forEach { it.isVisible = true }
            // NuPlayer on the Android 16 emulator reports position 0 once a video has ended.
            showTime(player.duration)
        }
        player.setOnSeekCompleteListener {
            seekingTo = null
            showTime()
        }
        player.setOnVideoSizeChangedListener { _, width, height ->
            if (width > 0 && height > 0) videoSize = Size(width, height)
            fitVideo()
        }
        player.setOnErrorListener { _, _, _ ->
            unplayable.isVisible = true
            true
        }
        runCatching {
            player.setDataSource(this, requireNotNull(uri))
            player.prepareAsync()
        }.onFailure { unplayable.isVisible = true }
    }

    private fun play() {
        hasEnded = false
        player.start()
        wantsToPlay = true
        keepScreenOn(true)
        showPlayPause()
        main.removeCallbacks(tick)
        tick.run()
    }

    private fun pause() {
        player.pause()
        wantsToPlay = false
        keepScreenOn(false)
        showPlayPause()
        showTime()
    }

    private fun togglePlay() {
        if (!isPrepared) return
        if (isPlaying) return pause()
        if (hasEnded) player.seekTo(0)
        play()
    }

    private fun skip(deltaMs: Int) {
        if (isPrepared) seek((seekingTo ?: player.currentPosition).coerceAtLeast(0) + deltaMs)
    }

    private fun seekTo(fraction: Float) {
        if (isPrepared && player.duration > 0) seek((player.duration * fraction).toInt())
    }

    /**
     * The player reaches the new place a moment later, so the time shows it at once, and a skip pressed again
     * before then counts from there.
     */
    private fun seek(toMs: Int) {
        val duration = player.duration
        val target = if (duration > 0) toMs.coerceIn(0, duration) else toMs.coerceAtLeast(0)
        hasEnded = false
        seekingTo = target
        player.seekTo(target)
        showTime(target)
    }

    /** Some videos opened from other apps have no known length; they show only the time played. */
    private fun showTime(atMs: Int = seekingTo ?: player.currentPosition) {
        if (!isPrepared) return
        val duration = player.duration
        if (duration <= 0) {
            time.text = formatDuration(atMs.coerceAtLeast(0).toLong())
            progress.fraction = 0f
            return
        }
        val at = atMs.coerceIn(0, duration)
        time.text = getString(R.string.video_time, formatDuration(at.toLong()), formatDuration(duration.toLong()))
        progress.fraction = at.toFloat() / duration
    }

    private fun showPlayPause() {
        val playing = isPlaying
        playPause.icon = if (playing) R.drawable.ic_pause else R.drawable.ic_play
        playPause.setText(if (playing) R.string.pause else R.string.play)
    }

    private fun toggleControls() {
        main.removeCallbacks(tick)
        if (Overlay.toggle(controls)) tick.run()
    }

    private fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * A TextureView stretches the video over the whole view; this undoes the stretch and places the video the way a
     * photo of its size would be, enlarged to fill the screen.
     */
    private fun fitVideo() {
        val video = videoSize ?: return
        if (texture.width == 0 || texture.height == 0) return
        val view = Size(texture.width, texture.height)
        val fitted = Viewport.fit(video, view, GalleryPrefs.rotatesLandscape(this), enlarges = true)
        texture.setTransform(Matrix(fitted).apply { preScale(video.width.toFloat() / view.width, video.height.toFloat() / view.height) })
    }

    private val surfaceListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            player.setSurface(Surface(surface))
            fitVideo()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = fitVideo()

        /** The window, and the texture with it, goes only after onDestroy has released the player. */
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            if (!isReleased) player.setSurface(null)
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }

    companion object {
        const val RESULT_DELETED = RESULT_FIRST_USER
        private const val EXTRA_VIDEO = "video"
        private const val STATE_POSITION = "position"
        private const val STATE_PLAYING = "playing"
        private const val SKIP_MS = 10_000
        private const val TICK_MS = 1000L

        fun intent(context: Context, video: Item): Intent = Intent(context, VideoActivity::class.java).putExtra(EXTRA_VIDEO, video.uri)
    }
}
