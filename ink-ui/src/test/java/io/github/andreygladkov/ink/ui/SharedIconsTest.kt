package io.github.andreygladkov.ink.ui

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.R
import io.github.andreygladkov.ink.eink.MonochromeIcons
import io.github.andreygladkov.ink.pixels
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedIconsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun everySharedIconRastersToTwoTonesAndIsNotEmpty() {
        val icons = R.drawable::class.java.fields.filter { it.name.startsWith("ic_") }
        assertTrue(icons.size >= 4)
        icons.forEach { field ->
            val pixels = MonochromeIcons.bitmap(context, field.getInt(null), 54, Color.BLACK).pixels()
            assertTrue(pixels.all { it == Color.BLACK || it == Color.TRANSPARENT }, "${field.name} has partial pixels")
            val filled = pixels.count { it == Color.BLACK }.toFloat() / pixels.size
            assertTrue(filled in 0.03f..0.8f, "${field.name} fills $filled of its square")
        }
    }
}
