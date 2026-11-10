package io.github.andreygladkov.inkgallery.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.util.Size
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.eink.GreyDither
import io.github.andreygladkov.ink.pixels
import io.github.andreygladkov.inkgallery.FakeMedia
import io.github.andreygladkov.inkgallery.FakeMedia.Row
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FramesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver = context.contentResolver

    @Before fun library() = FakeMedia.install()

    @Test fun decodesAtTheSizeAskedForAndRemembersTheFullSize() {
        FakeMedia.add(context, Row(1, "ramp.png", "camera", "Camera", 0, width = 300, height = 200))
        var offered: Size? = null
        val decoded = Frames.decode(resolver, FakeMedia.uri(1)) { offered = it; Size(150, 100) }
        assertEquals(Size(300, 200), offered)
        assertEquals(Size(300, 200), decoded.photo)
        assertEquals(150 to 100, decoded.bitmap.width to decoded.bitmap.height)
    }

    @Test fun aPhotoTakenSidewaysComesUpright() {
        FakeMedia.add(context, Row(1, "sideways.jpg", "camera", "Camera", 0, width = 300, height = 200))
        FakeMedia.files.getValue(1).apply {
            outputStream().use { FakeMedia.ramp(300, 200).compress(Bitmap.CompressFormat.JPEG, 90, it) }
            ExifInterface(path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
        }
        val decoded = Frames.decode(resolver, FakeMedia.uri(1)) { it }
        assertEquals(Size(200, 300), decoded.photo)
        assertEquals(200 to 300, decoded.bitmap.width to decoded.bitmap.height)
    }

    @Test fun aFrameForClearHasOnlyThePanelsGreys() {
        FakeMedia.add(context, Row(1, "ramp.png", "camera", "Camera", 0, width = 300, height = 200))
        val view = Size(100, 120)
        val decoded = Frames.decode(resolver, FakeMedia.uri(1)) { Viewport.decodedSize(Viewport.fit(it, view, false), it) }
        val frame = Frames.render(decoded, Viewport.fit(decoded.photo, view, false), view, Color.WHITE, dither = true)
        val greys = frame.pixels().map { Color.red(it) }.toSet()
        assertTrue(greys.all { it % GreyDither.STEP == 0 }, "greys: $greys")
        assertTrue(greys.size > 8, "a ramp keeps its range: $greys")
    }

    @Test fun aFrameOutsideClearKeepsThePhotosOwnTonesOnThePage() {
        FakeMedia.add(context, Row(1, "ramp.png", "camera", "Camera", 0, width = 300, height = 200))
        val view = Size(100, 120)
        val decoded = Frames.decode(resolver, FakeMedia.uri(1)) { Viewport.decodedSize(Viewport.fit(it, view, false), it) }
        val frame = Frames.render(decoded, Viewport.fit(decoded.photo, view, false), view, Color.BLACK, dither = false)
        assertEquals(Color.BLACK, frame.getPixel(50, 5))
        assertTrue(frame.pixels().map { Color.red(it) }.any { it % GreyDither.STEP != 0 })
    }
}
