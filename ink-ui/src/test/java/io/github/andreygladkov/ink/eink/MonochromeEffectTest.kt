package io.github.andreygladkov.ink.eink

import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import androidx.core.graphics.createBitmap
import io.github.andreygladkov.ink.pixels
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonochromeEffectTest {
    private fun filtered(colors: List<Int>, ink: Int = Color.BLACK): List<Int> {
        val source = createBitmap(colors.size, 1).apply { colors.forEachIndexed { x, color -> setPixel(x, 0, color) } }
        val node = RenderNode("widget").apply {
            setPosition(0, 0, colors.size, 1)
            setRenderEffect(MonochromeEffect.renderEffect(ink))
            beginRecording().drawBitmap(source, 0f, 0f, null)
            endRecording()
        }
        val usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        ImageReader.newInstance(colors.size, 1, PixelFormat.RGBA_8888, 1, usage).use { reader ->
            val renderer = HardwareRenderer().apply {
                setSurface(reader.surface)
                setContentRoot(node)
            }
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            renderer.destroy()
            reader.acquireNextImage().use { image ->
                val plane = image.planes[0]
                val out = createBitmap(plane.rowStride / plane.pixelStride, 1)
                out.copyPixelsFromBuffer(plane.buffer)
                return out.pixels().take(colors.size)
            }
        }
    }

    private fun filtered(color: Int, ink: Int = Color.BLACK) = filtered(listOf(color), ink).single()

    @Test fun everyGreyAndEveryTransparencyLandsOnInkOrPage() {
        val colors = (0..255 step 5).flatMap { grey -> (0..255 step 15).map { alpha -> Color.argb(alpha, grey, grey, grey) } }
        val outcomes = filtered(colors).distinct().toSet()
        assertEquals(setOf(Color.BLACK, Color.TRANSPARENT), outcomes)
    }

    @Test fun itAgreesWithDitherLuminanceOnWhichSideAColourFalls() {
        val colors = listOf(
            Color.rgb(0xFF, 0x44, 0x22), Color.rgb(0x22, 0x55, 0xEE), Color.rgb(0xFF, 0xE0, 0x40),
            Color.rgb(0x75, 0x75, 0x75), Color.rgb(0x8A, 0x8A, 0x8A), Color.argb(0x8A, 0, 0, 0), Color.argb(0x4D, 0, 0, 0),
        )
        val expected = colors.map { if (Dither.luminance(it) < Dither.THRESHOLD) Color.BLACK else Color.TRANSPARENT }
        assertEquals(expected, filtered(colors))
    }

    @Test fun aSaturatedBackgroundBecomesInkAndWhiteBecomesThePage() {
        assertEquals(Color.BLACK, filtered(Color.rgb(0xFF, 0x44, 0x22)))
        assertEquals(Color.TRANSPARENT, filtered(Color.WHITE))
    }

    @Test fun anInvertedStyleDrawsDarkPixelsInWhite() {
        assertEquals(Color.WHITE, filtered(Color.BLACK, ink = Color.WHITE))
        assertEquals(Color.TRANSPARENT, filtered(Color.WHITE, ink = Color.WHITE))
    }
}
