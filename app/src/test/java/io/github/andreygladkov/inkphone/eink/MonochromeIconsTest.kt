package io.github.andreygladkov.inkphone.eink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.pixels
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonochromeIconsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val icons = R.drawable::class.java.fields.filter { it.name.startsWith("ic_") && it.name != "ic_launcher_foreground" }

    @Test fun everyIconRastersToTwoTonesAndIsNotEmpty() {
        assertTrue(icons.size > 10)
        icons.forEach { field ->
            val pixels = MonochromeIcons.bitmap(context, field.getInt(null), 54, Color.BLACK).pixels()
            assertTrue(pixels.all { it == Color.BLACK || it == Color.TRANSPARENT }, "${field.name} has partial pixels")
            val filled = pixels.count { it == Color.BLACK }.toFloat() / pixels.size
            assertTrue(filled in 0.03f..0.8f, "${field.name} fills $filled of its square")
        }
    }

    @Test fun cachedIconIsNotRescaledOnCanvasOfAnotherDensity() {
        val icon = MonochromeIcons.bitmap(context, R.drawable.ic_call, 54, Color.BLACK)
        val target = createBitmap(80, 80).apply { density = 480 }
        Canvas(target).apply {
            drawColor(Color.WHITE)
            drawBitmap(icon, 0f, 0f, null)
        }
        assertTrue(target.pixels().all { it == Color.BLACK || it == Color.WHITE })
    }
}
