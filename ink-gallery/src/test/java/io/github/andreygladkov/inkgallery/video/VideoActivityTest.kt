package io.github.andreygladkov.inkgallery.video

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Looper
import android.view.TextureView
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkgallery.A9
import io.github.andreygladkov.inkgallery.FakeMedia
import io.github.andreygladkov.inkgallery.FakeMedia.Row
import io.github.andreygladkov.inkgallery.drainBackground
import io.github.andreygladkov.inkgallery.media.Item
import io.github.andreygladkov.inkgallery.registerVideo
import io.github.andreygladkov.inkgallery.viewer.ViewerActivity
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class VideoActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val clip = Item(FakeMedia.videoUri(7), "clip.mp4", isVideo = true, isInLibrary = true, albumId = "camera", durationMs = 90_000)

    @Before fun library() {
        FakeMedia.install()
        FakeMedia.add(context, Row(7, "clip.mp4", "camera", "Camera", takenAt = 2_000, durationMs = 90_000))
        FakeMedia.add(context, Row(1, "photo.png", "camera", "Camera", takenAt = 1_000))
        registerVideo(context, clip.uri, 90_000)
    }

    @After fun settle() = drainBackground()

    private fun play(): ActivityController<VideoActivity> {
        val controller = Robolectric.buildActivity(VideoActivity::class.java, VideoActivity.intent(context, clip)).setup()
        idleUntil { controller.get().isPlaying }
        return controller
    }

    private fun Activity.button(label: String) = contentRoot(this).find<InkButton>().single { it.text.toString() == label }

    private fun Activity.time() = contentRoot(this).find<InkTextView>().first { it.tag == "ink_header_title" }.text.toString()

    private fun Activity.tapScreen() = contentRoot(this).find<TextureView>().single().performClick()

    private fun elapse(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun aVideoPlaysAtOnceWithTheControlsHiddenAndTheScreenKeptOn() {
        val activity = play().get()
        assertFalse(activity.button("Пауза").isShown)
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
    }

    @Test fun theControlsShowTheTimeAndMoveTheVideo() {
        val activity = play().get()
        activity.tapScreen()
        assertTrue(activity.button("Пауза").isShown)
        assertEquals("0:00 / 1:30", activity.time())
        elapse(3_000)
        assertEquals("0:03 / 1:30", activity.time())

        activity.button("+10 с").performClick()
        assertEquals("0:13 / 1:30", activity.time())
        elapse(0)
        assertEquals(13, activity.position / 1000)
        activity.button("−10 с").performClick()
        activity.button("−10 с").performClick()
        elapse(0)
        assertEquals(0, activity.position / 1000)

        activity.button("Пауза").performClick()
        assertFalse(activity.isPlaying)
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0)
        activity.button("Играть").performClick()
        assertTrue(activity.isPlaying)

        activity.tapScreen()
        assertFalse(activity.button("Пауза").isShown)
    }

    @Test fun theEndLeavesTheLastFrameWithTheControlsAndPlaysAgainFromTheStart() {
        val activity = play().get()
        elapse(91_000)
        assertFalse(activity.isPlaying)
        assertTrue(activity.button("Играть").isShown)
        assertEquals("1:30 / 1:30", activity.time())
        activity.button("Играть").performClick()
        assertTrue(activity.isPlaying)
        assertTrue(activity.position < 1_000)
    }

    @Test fun leavingPausesAndComingBackPlaysOn() {
        val controller = play()
        elapse(5_000)
        controller.pause().stop()
        assertFalse(controller.get().isPlaying)
        elapse(5_000)
        controller.restart().resume()
        assertTrue(controller.get().isPlaying)
        assertEquals(5, controller.get().position / 1000)
    }

    @Test fun aVideoReadyOnlyAfterTheAppWasLeftWaitsForItsReturn() {
        val controller = Robolectric.buildActivity(VideoActivity::class.java, VideoActivity.intent(context, clip)).create().start().resume()
        controller.pause().stop()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(controller.get().isPlaying)
        assertTrue(controller.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0)
        controller.restart().resume()
        assertTrue(controller.get().isPlaying)
    }

    @Test fun pausedAndPlayedAgainItGoesOnFromThere() {
        val activity = play().get()
        elapse(4_000)
        activity.tapScreen()
        activity.button("Пауза").performClick()
        activity.button("Играть").performClick()
        assertEquals(4, activity.position / 1000)
    }

    @Test fun aVideoOfUnknownLengthShowsTheTimePlayedAndSkipsForward() {
        val endless = Item(FakeMedia.videoUri(9), "stream.mp4", isVideo = true)
        registerVideo(context, endless.uri, -1)
        val activity = Robolectric.buildActivity(VideoActivity::class.java, VideoActivity.intent(context, endless)).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        activity.tapScreen()
        activity.button("+10 с").performClick()
        assertEquals("0:10", activity.time())
        contentRoot(activity).find<ProgressLine>().single().apply {
            val time = android.os.SystemClock.uptimeMillis()
            dispatchTouchEvent(android.view.MotionEvent.obtain(time, time, android.view.MotionEvent.ACTION_UP, width / 2f, 1f, 0))
        }
        assertEquals("0:10", activity.time())
    }

    @Test fun aVideoOpenedFromTheGridPlaysInSpeedAndBackGivesTheUsualModeBack() {
        A9.installService(context)
        val video = play()
        assertEquals(listOf("TEMPORARY SPEED"), A9.modeChanges(context))
        video.pause().stop().destroy()
        assertEquals(listOf("TEMPORARY SPEED", "RESTORE"), A9.modeChanges(context))
    }

    @Test fun aVideoOpenedFromAPhotoGoesBackToClear() {
        A9.installService(context)
        val viewer = Robolectric.buildActivity(ViewerActivity::class.java, ViewerActivity.intent(context, clip, null)).setup()
        idleUntil { viewer.get().shownPhoto != null }
        val video = Robolectric.buildActivity(VideoActivity::class.java, VideoActivity.intent(context, clip)).create().start()
        viewer.pause().stop()
        video.resume()
        video.pause()
        viewer.restart().resume()
        video.stop().destroy()
        assertEquals(listOf("TEMPORARY CLEAR", "TEMPORARY SPEED", "TEMPORARY CLEAR"), A9.modeChanges(context))
        viewer.pause().stop()
        assertEquals(listOf("TEMPORARY CLEAR", "TEMPORARY SPEED", "TEMPORARY CLEAR", "RESTORE"), A9.modeChanges(context))
    }

    @Test fun aVideoAnotherAppOpensPlaysToo() {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(clip.uri, "video/mp4")
        val controller = Robolectric.buildActivity(VideoActivity::class.java, intent).setup()
        idleUntil { controller.get().isPlaying }
    }

    @Test fun anUnplayableVideoSaysSo() {
        val broken = Item(FakeMedia.videoUri(99), "broken.mp4", isVideo = true)
        val activity = Robolectric.buildActivity(VideoActivity::class.java, VideoActivity.intent(context, broken)).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(contentRoot(activity).find<InkTextView>().any { it.isShown && it.text.toString() == "Это видео не воспроизвести" })
    }
}
