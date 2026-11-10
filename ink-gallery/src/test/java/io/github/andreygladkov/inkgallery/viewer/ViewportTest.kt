package io.github.andreygladkov.inkgallery.viewer

import android.graphics.RectF
import android.util.Size
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ViewportTest {
    private val view = Size(824, 1500)

    private fun RectF.whole() = listOf(left, top, right, bottom).all { it == it.roundToInt().toFloat() }

    @Test fun aTallPhotoFillsTheWidthCentredAndWholePixel() {
        val photo = Size(3000, 4000)
        val shown = Viewport.bounds(Viewport.fit(photo, view, rotateLandscape = false), photo)
        assertEquals(824f, shown.width())
        assertEquals(1099f, shown.height())
        assertEquals(200f, shown.top)
        assertTrue(shown.whole())
    }

    @Test fun aSmallPictureIsNotEnlarged() {
        val photo = Size(200, 100)
        val shown = Viewport.bounds(Viewport.fit(photo, view, rotateLandscape = false), photo)
        assertEquals(RectF(312f, 700f, 512f, 800f), shown)
    }

    @Test fun aWidePhotoTurnsToLieAlongTheScreenWithItsTopOnTheRight() {
        val photo = Size(4000, 3000)
        val fitted = Viewport.fit(photo, view, rotateLandscape = true)
        val shown = Viewport.bounds(fitted, photo)
        assertEquals(824f to 1099f, shown.width() to shown.height())
        val topLeft = floatArrayOf(0f, 0f).also(fitted::mapPoints)
        assertEquals(824f, topLeft[0])
        assertEquals(Size(1099, 824), Viewport.decodedSize(fitted, photo))
    }

    @Test fun withoutTurningAWidePhotoKeepsItsSide() {
        val photo = Size(4000, 3000)
        assertEquals(Size(824, 618), Viewport.decodedSize(Viewport.fit(photo, view, rotateLandscape = false), photo))
    }

    @Test fun zoomingDoublesTheSizeAroundTheTapAndCoversTheScreen() {
        val photo = Size(3000, 4000)
        val fitted = Viewport.fit(photo, view, rotateLandscape = false)
        val zoomed = Viewport.zoomed(fitted, 412f, 750f, photo, view)
        val shown = Viewport.bounds(zoomed, photo)
        assertEquals(1648f to 2198f, shown.width() to shown.height())
        assertTrue(shown.left <= 0 && shown.right >= view.width && shown.top <= 0 && shown.bottom >= view.height)
        assertTrue(shown.whole())
        assertEquals(Size(1648, 2198), Viewport.decodedSize(zoomed, photo))
    }

    @Test fun aZoomNearTheEdgeIsPulledBackSoNoGapShows() {
        val photo = Size(3000, 4000)
        val zoomed = Viewport.zoomed(Viewport.fit(photo, view, false), 0f, 0f, photo, view)
        val shown = Viewport.bounds(zoomed, photo)
        assertEquals(0f to 0f, shown.left to shown.top)
    }

    @Test fun panningStopsAtTheEdges() {
        val photo = Size(3000, 4000)
        val zoomed = Viewport.zoomed(Viewport.fit(photo, view, false), 412f, 750f, photo, view)
        val far = Viewport.bounds(Viewport.panned(zoomed, -10_000f, 10_000f, photo, view), photo)
        assertEquals(view.width.toFloat() to 0f, far.right to far.top)
        val slight = Viewport.bounds(Viewport.panned(zoomed, -100.4f, 0f, photo, view), photo)
        assertEquals(Viewport.bounds(zoomed, photo).left - 100, slight.left)
    }

    @Test fun aPictureIsNeverDecodedAboveItsOwnSize() {
        val photo = Size(1000, 800)
        val zoomed = Viewport.zoomed(Viewport.fit(photo, view, false), 412f, 750f, photo, view)
        assertEquals(photo, Viewport.decodedSize(zoomed, photo))
    }
}
