package io.github.andreygladkov.ink.ui

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.render
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class InkButtonTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun pressedBorderedButtonKeepsItsRoundedCorners() {
        val bitmap = render(pressed { isBordered = true }, width = 300, height = 150)
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
        assertEquals(Color.BLACK, bitmap.getPixel(150, 2))
    }

    @Test fun pressedBorderlessButtonFillsItsCellSquare() {
        val bitmap = render(pressed { isBordered = false }, width = 300, height = 150)
        assertEquals(Color.BLACK, bitmap.getPixel(0, 0))
    }

    @Test fun borderlessButtonCanRoundItsFill() {
        val bitmap = render(pressed { isBordered = false; isFillRounded = true }, width = 300, height = 150)
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
        assertEquals(Color.BLACK, bitmap.getPixel(150, 2))
    }

    @Test fun aJoinedSideKeepsSquareCorners() {
        val bitmap = render(pressed { isJoinedEnd = true }, width = 300, height = 150)
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
        assertEquals(Color.BLACK, bitmap.getPixel(299, 0))
        assertEquals(Color.BLACK, bitmap.getPixel(299, 149))
    }

    private fun pressed(setup: InkButton.() -> Unit) = InkButton(context).apply(setup).apply { isPressed = true }
}
