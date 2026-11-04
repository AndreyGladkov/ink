package io.github.andreygladkov.ink.eink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.R
import io.github.andreygladkov.ink.pixels
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonochromeIconsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun cachedIconIsNotRescaledOnCanvasOfAnotherDensity() {
        val icon = MonochromeIcons.bitmap(context, R.drawable.ic_page_up, 54, Color.BLACK)
        val target = createBitmap(80, 80).apply { density = 480 }
        Canvas(target).apply {
            drawColor(Color.WHITE)
            drawBitmap(icon, 0f, 0f, null)
        }
        assertTrue(target.pixels().all { it == Color.BLACK || it == Color.WHITE })
    }
}
