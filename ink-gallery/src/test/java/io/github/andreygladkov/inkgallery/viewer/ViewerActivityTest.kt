package io.github.andreygladkov.inkgallery.viewer

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.eink.A9Panel
import io.github.andreygladkov.ink.eink.GreyDither
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.pixels
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkgallery.A9
import io.github.andreygladkov.inkgallery.FakeMedia
import io.github.andreygladkov.inkgallery.FakeMedia.Row
import io.github.andreygladkov.inkgallery.FakeShared
import io.github.andreygladkov.inkgallery.deliverResult
import io.github.andreygladkov.inkgallery.drainBackground
import io.github.andreygladkov.inkgallery.registerVideo
import io.github.andreygladkov.inkgallery.media.Item
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class ViewerActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @After fun settle() = drainBackground()

    @Before fun library() {
        FakeMedia.install()
        FakeMedia.add(context, Row(1, "first.png", "camera", "Camera", takenAt = 3_000))
        FakeMedia.add(context, Row(2, "second.png", "shots", "Screenshots", takenAt = 2_000))
        FakeMedia.add(context, Row(3, "third.png", "camera", "Camera", takenAt = 1_000))
    }

    private fun open(id: Long, album: String? = null): ActivityController<ViewerActivity> {
        val photo = Item(FakeMedia.uri(id), "", albumId = album)
        val controller = Robolectric.buildActivity(ViewerActivity::class.java, ViewerActivity.intent(context, photo, album)).setup()
        idleUntil { controller.get().shownPhoto != null }
        return controller
    }

    private fun Activity.counter() = contentRoot(this).find<InkTextView>().first { it.tag == "ink_header_title" }.text.toString()

    private fun Activity.button(label: String) = contentRoot(this).find<InkButton>().single { it.text.toString() == label }

    private fun Activity.photoView() = contentRoot(this).find<PhotoView>().single()

    private fun PhotoView.tap(x: Float) {
        val time = SystemClock.uptimeMillis()
        dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, height / 2f, 0))
        dispatchTouchEvent(MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, x, height / 2f, 0))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
    }

    private fun PhotoView.swipe(fromX: Float, toX: Float) {
        val time = SystemClock.uptimeMillis()
        dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, fromX, height / 2f, 0))
        dispatchTouchEvent(MotionEvent.obtain(time, time + 30, MotionEvent.ACTION_MOVE, (fromX + toX) / 2, height / 2f, 0))
        dispatchTouchEvent(MotionEvent.obtain(time, time + 60, MotionEvent.ACTION_UP, toX, height / 2f, 0))
    }

    private fun PhotoView.doubleTap(x: Float, y: Float) {
        val time = SystemClock.uptimeMillis()
        listOf(0L, 100L).forEach { offset ->
            dispatchTouchEvent(MotionEvent.obtain(time + offset, time + offset, MotionEvent.ACTION_DOWN, x, y, 0))
            dispatchTouchEvent(MotionEvent.obtain(time + offset, time + offset + 30, MotionEvent.ACTION_UP, x, y, 0))
        }
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
    }

    private fun PhotoView.drawn() = render(this, width, height).pixels().toList()

    private fun widePicture() = createBitmap(1200, 1600).apply {
        Canvas(this).drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 1200f, 0f, Color.BLACK, Color.WHITE, Shader.TileMode.CLAMP) })
    }

    @Test fun aDoubleTapInTheMiddleZoomsInADragMovesAndAnotherDoubleTapZoomsOut() {
        FakeMedia.add(context, Row(4, "big.png", "camera", "Camera", takenAt = 4_000, width = 1200, height = 1600), widePicture())
        val activity = open(4).get()
        val view = activity.photoView()
        idleUntil { view.drawn().toSet().size > 2 }
        val fitted = view.drawn()

        view.doubleTap(view.width / 2f, view.height / 2f)
        assertTrue(view.isZoomed)
        idleUntil { view.drawn() != fitted }
        val zoomed = view.drawn()
        assertEquals("1 / 4", activity.counter(), "a double tap turned the photo")

        view.swipe(view.width * 0.7f, view.width * 0.3f)
        assertEquals("1 / 4", activity.counter(), "a drag while zoomed turned the photo")
        idleUntil { view.drawn() != zoomed }

        view.doubleTap(view.width / 2f, view.height / 2f)
        assertFalse(view.isZoomed)
        idleUntil { view.drawn() == fitted }
    }

    @Test fun aDoubleTapRightAfterTurningDoesNotZoomThePhotoTurnedFrom() {
        FakeMedia.add(context, Row(4, "black.png", "camera", "Camera", takenAt = 2_500), createBitmap(30, 20).apply { eraseColor(Color.BLACK) })
        val activity = open(1).get()
        val view = activity.photoView()
        idleUntil { view.drawn().toSet().size > 2 }
        val first = view.drawn()
        val time = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, view.width * 0.9f, 10f, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(time, time + 20, MotionEvent.ACTION_UP, view.width * 0.9f, 10f, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(time + 400, time + 400, MotionEvent.ACTION_DOWN, view.width / 2f, view.height / 2f, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(time + 400, time + 420, MotionEvent.ACTION_UP, view.width / 2f, view.height / 2f, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(time + 500, time + 500, MotionEvent.ACTION_DOWN, view.width / 2f, view.height / 2f, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(time + 500, time + 520, MotionEvent.ACTION_UP, view.width / 2f, view.height / 2f, 0))
        assertFalse(view.isZoomed)
        assertEquals("black.png", activity.shownPhoto?.name)
        idleUntil { view.drawn() != first }
    }

    @Test fun aDeletionAnsweredBeforeTheListIsReadAgainKeepsTheViewerOpen() {
        val controller = Robolectric.buildActivity(ViewerActivity::class.java, ViewerActivity.intent(context, Item(FakeMedia.uri(2), ""), null))
        val saved = android.os.Bundle().apply { putString("photo", FakeMedia.uri(2).toString()); putString("deleting", FakeMedia.uri(2).toString()) }
        controller.create(saved)
        val activity = controller.get()
        activity.deliverResult(Actions.REQUEST_DELETE, Activity.RESULT_OK)
        assertFalse(activity.isFinishing)
        controller.start().resume()
        idleUntil { activity.shownPhoto != null }
    }

    @Test fun opensAmongAllPhotosNewestFirst() {
        val activity = open(2).get()
        assertEquals("2 / 3", activity.counter())
        assertEquals("second.png", activity.shownPhoto?.name)
    }

    @Test fun opensAmongTheAlbumItCameFrom() {
        val activity = open(3, album = "camera").get()
        assertEquals("2 / 2", activity.counter())
    }

    @Test fun theOuterThirdsAndSwipesTurnPhotosAndTheMiddleShowsTheControls() {
        val activity = open(1).get()
        val view = activity.photoView()
        view.tap(view.width * 0.9f)
        assertEquals("2 / 3", activity.counter())
        view.swipe(view.width * 0.8f, view.width * 0.2f)
        assertEquals("3 / 3", activity.counter())
        view.tap(view.width * 0.9f)
        assertEquals("3 / 3", activity.counter(), "turned past the last photo")
        view.tap(view.width * 0.1f)
        assertEquals("2 / 3", activity.counter())

        val share = activity.button("Поделиться")
        assertFalse(share.isShown)
        view.tap(view.width * 0.5f)
        assertTrue(share.isShown)
        assertEquals("2 / 3", activity.counter())
    }

    @Test fun thePhotoIsDrawnOnceDecoded() {
        val activity = open(1).get()
        val view = activity.photoView()
        idleUntil { render(view, view.width, view.height).pixels().toSet().size > 2 }
    }

    @Test fun withTheA9ServiceAPhotoAndItsDetailsAreShownInClearUntilTheyLeaveTheScreen() {
        A9.installService(context)
        val viewer = open(1)
        assertEquals(listOf("TEMPORARY CLEAR"), A9.modeChanges(context))
        viewer.get().photoView().tap(viewer.get().photoView().width * 0.9f)
        assertEquals(listOf("TEMPORARY CLEAR"), A9.modeChanges(context), "turning photos switches no modes")

        val info = Robolectric.buildActivity(InfoActivity::class.java, InfoActivity.intent(context, viewer.get().shownPhoto!!)).create().start()
        viewer.pause().stop()
        info.resume().pause()
        viewer.restart().resume()
        info.stop().destroy()
        assertEquals(listOf("TEMPORARY CLEAR"), A9.modeChanges(context), "the details of a photo switch no modes")

        viewer.pause().stop()
        assertEquals(listOf("TEMPORARY CLEAR", "RESTORE"), A9.modeChanges(context))
    }

    @Test fun aPhotoScreenBeingRecreatedKeepsClear() {
        A9.installService(context)
        open(1).recreate()
        assertEquals(listOf("TEMPORARY CLEAR"), A9.modeChanges(context))
    }

    @Test fun aNewThemeIsNotShownWithFramesPreparedInTheOldOne() {
        open(1).pause().stop()
        context.inkPrefs().edit { putBoolean(io.github.andreygladkov.ink.eink.InkStyle.PREF_INVERTED, true) }
        val view = open(2).get().photoView()
        idleUntil { view.drawn().toSet().size > 2 }
        assertEquals(Color.BLACK, render(view, view.width, view.height).getPixel(1, 1))
    }

    @Test fun inClearThePhotoIsDitheredToThePanelsGreys() {
        A9.installService(context)
        val view = open(1).get().photoView()
        idleUntil { render(view, view.width, view.height).pixels().toSet().size > 2 }
        val greys = render(view, view.width, view.height).pixels().map { Color.red(it) }.toSet()
        assertTrue(greys.all { it % GreyDither.STEP == 0 }, "greys: $greys")
    }

    private fun shownGreys(id: Long): Set<Int> {
        val view = open(id).get().photoView()
        idleUntil { view.drawn().toSet().size > 2 }
        return view.drawn().map { Color.red(it) }.toSet()
    }

    @Test fun ditheringCanBeTurnedOffInClear() {
        A9.installService(context)
        PhotoDither.choose(context, PhotoDither.OFF)
        val greys = shownGreys(1)
        assertTrue(greys.any { it % GreyDither.STEP != 0 }, "greys: $greys")
        assertEquals(listOf("TEMPORARY CLEAR"), A9.modeChanges(context))
    }

    @Test fun ditheringCanStayOnWithoutSwitchingToClear() {
        A9.installService(context)
        context.inkPrefs().edit { putBoolean(ClearMode.PREF, false) }
        PhotoDither.choose(context, PhotoDither.ON)
        val greys = shownGreys(1)
        assertTrue(greys.all { it % GreyDither.STEP == 0 }, "greys: $greys")
        assertEquals(emptyList(), A9.modeChanges(context))
    }

    @Test fun byDefaultDitheringFollowsClear() {
        A9.installService(context)
        context.inkPrefs().edit { putBoolean(ClearMode.PREF, false) }
        assertTrue(shownGreys(1).any { it % GreyDither.STEP != 0 })
    }

    @Test fun withoutTheA9ServicePhotosAreNeverDithered() {
        PhotoDither.choose(context, PhotoDither.ON)
        assertTrue(shownGreys(1).any { it % GreyDither.STEP != 0 })
    }

    @Test fun clearIsLeftAloneWhenTurnedOffOrWithoutTheService() {
        open(1).pause().stop()
        A9.installService(context)
        context.inkPrefs().edit { putBoolean(ClearMode.PREF, false) }
        open(2).pause().stop()
        assertEquals(emptyList(), A9.modeChanges(context))
    }

    @Test fun turningAPhotoCountsAsAPageForTheFullRefresh() {
        A9.installService(context)
        context.inkPrefs().edit { putInt(A9Panel.PREF_CLEAR_EVERY_PAGES, 1) }
        val view = open(1).get().photoView()
        view.tap(view.width * 0.9f)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(900))
        assertEquals(1, shadowOf(context).broadcastIntents.count { it.action == "EINK_FORCE_CLEAR" })
    }

    private fun Activity.shownTexts() = contentRoot(this).find<InkTextView>().filter { it.isShown }.map { it.text.toString() }

    @Test fun deletingAsksInPlaceThenTheSystemAndMovesOn() {
        val activity = open(2).get()
        activity.photoView().tap(activity.photoView().width * 0.5f)
        activity.button("Удалить").performClick()
        assertTrue("Удалить фото?" in activity.shownTexts(), "shown: ${activity.shownTexts()}")
        assertFalse(activity.button("Поделиться").isShown)
        activity.button("Отмена").performClick()
        assertTrue(FakeMedia.deleteRequests.isEmpty())
        assertTrue(activity.button("Поделиться").isShown)

        activity.button("Удалить").performClick()
        activity.button("OK").performClick()
        assertEquals(listOf(FakeMedia.uri(2)), FakeMedia.deleteRequests)
        activity.deliverResult(Actions.REQUEST_DELETE, Activity.RESULT_OK)
        assertEquals("2 / 2", activity.counter())
        assertEquals("third.png", activity.shownPhoto?.name)
        assertTrue(activity.button("Поделиться").isShown)
    }

    @Test fun theDeleteQuestionIsBlackOnWhiteWithALineBetweenItsAnswers() {
        val activity = open(2).get()
        activity.photoView().tap(activity.photoView().width * 0.5f)
        activity.button("Удалить").performClick()
        val ok = activity.button("OK")
        val row = ok.parent as io.github.andreygladkov.ink.ui.InkButtonRow
        assertTrue((row.dividerDrawable as android.graphics.drawable.InsetDrawable).drawable is io.github.andreygladkov.ink.ui.VerticalLineDrawable)
        assertEquals(android.widget.LinearLayout.SHOW_DIVIDER_MIDDLE, row.showDividers)
        assertEquals(Color.BLACK, ok.currentTextColor)
        assertEquals(Color.BLACK, activity.button("Отмена").currentTextColor)
    }

    @Test fun aVideoAmongThePhotosShowsItsFirstFrameAndAPlayButton() {
        FakeMedia.add(context, Row(8, "clip.mp4", "camera", "Camera", takenAt = 2_500, durationMs = 90_000))
        registerVideo(context, FakeMedia.videoUri(8), 90_000)
        val activity = open(1).get()
        val play = contentRoot(activity).find<InkButton>().single { it.contentDescription == "Играть" }
        assertFalse(play.isShown)
        val view = activity.photoView()
        view.tap(view.width * 0.9f)
        assertEquals("clip.mp4", activity.shownPhoto?.name)
        assertTrue(play.isShown)
        idleUntil { view.drawn().toSet().size > 2 }
        play.performClick()
        val started = assertNotNull(shadowOf(activity).nextStartedActivity)
        assertEquals(io.github.andreygladkov.inkgallery.video.VideoActivity::class.java.name, started.component?.className)
        view.tap(view.width * 0.9f)
        assertFalse(play.isShown)
    }

    @Test fun aVideoDeletedInThePlayerLeavesTheViewersTurns() {
        FakeMedia.add(context, Row(8, "clip.mp4", "camera", "Camera", takenAt = 2_500, durationMs = 90_000))
        registerVideo(context, FakeMedia.videoUri(8), 90_000)
        val controller = Robolectric.buildActivity(ViewerActivity::class.java,
            ViewerActivity.intent(context, Item(FakeMedia.videoUri(8), "", isVideo = true), null)).setup()
        idleUntil { controller.get().shownPhoto != null }
        val activity = controller.get()
        assertEquals("2 / 4", activity.counter())
        activity.deliverResult(2, io.github.andreygladkov.inkgallery.video.VideoActivity.RESULT_DELETED, Intent().setData(FakeMedia.videoUri(8)))
        assertEquals("2 / 3", activity.counter())
        assertEquals("second.png", activity.shownPhoto?.name)
    }

    @Test fun detailsOpenForTheShownPhoto() {
        val activity = open(2).get()
        activity.button("Сведения").performClick()
        val started = assertNotNull(shadowOf(activity).nextStartedActivity)
        val info = Robolectric.buildActivity(InfoActivity::class.java, started).setup().get()
        val texts = contentRoot(info).find<InkTextView>().map { it.text.toString() }
        assertTrue(listOf("Файл", "second.png", "Папка", "DCIM/Screenshots", "Разрешение", "30 × 20").all { it in texts }, "shown: $texts")
    }

    @Test fun aPictureFromAnotherAppIsShownAloneWithoutDelete() {
        FakeShared.install(context)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(FakeShared.uri, "image/png")
        val activity = Robolectric.buildActivity(ViewerActivity::class.java, intent).setup().get()
        idleUntil { activity.shownPhoto != null }
        assertEquals("shared.png", activity.counter())
        assertEquals(View.GONE, activity.button("Удалить").visibility)
        val view = activity.photoView()
        view.tap(view.width * 0.9f)
        assertEquals("shared.png", activity.shownPhoto?.name)
    }
}
