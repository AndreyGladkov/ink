package io.github.andreygladkov.inkweather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.eink.MonochromeIcons
import io.github.andreygladkov.ink.pixels
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppIconsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val icons = R.drawable::class.java.fields.filter { it.name.startsWith("ic_") && it.name != "ic_launcher_foreground" }

    @Test fun everyIconRastersToTwoTonesAndIsNotEmpty() {
        assertTrue(icons.isNotEmpty())
        icons.forEach { field ->
            val pixels = MonochromeIcons.bitmap(context, field.getInt(null), 54, Color.BLACK).pixels()
            assertTrue(pixels.all { it == Color.BLACK || it == Color.TRANSPARENT }, "${field.name} has partial pixels")
            val filled = pixels.count { it == Color.BLACK }.toFloat() / pixels.size
            assertTrue(filled in 0.03f..0.8f, "${field.name} fills $filled of its square")
        }
    }

    // Launchers mask adaptive icons to arbitrary shapes; only a centred circle of 66 of the 108 units is guaranteed visible.
    @Test fun launcherIconStaysInsideAdaptiveSafeZone() {
        val size = 108 * 4
        val drawable = context.getDrawable(R.drawable.ic_launcher_foreground)!!.apply { setBounds(0, 0, size, size) }
        val bitmap = createBitmap(size, size).also { drawable.draw(Canvas(it)) }
        val pixels = bitmap.pixels()
        val center = size / 2f
        val safeRadius = 33 * 4f
        val outside = pixels.indices.count { i ->
            Color.alpha(pixels[i]) != 0 && hypot(i % size + 0.5f - center, i / size + 0.5f - center) > safeRadius
        }
        assertTrue(pixels.count { Color.alpha(it) != 0 } > size * size / 20, "launcher icon is almost empty")
        assertEquals(0, outside, "launcher icon pixels outside the safe zone")
    }
}
