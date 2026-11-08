package io.github.andreygladkov.inklauncher.ui

import android.app.Application
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.pixels
import io.github.andreygladkov.ink.render
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class ScreenTimeViewTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val metrics = context.resources.displayMetrics
    private val padding = (8 * metrics.density).roundToInt()
    private val fullSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, InkStyle.of(context).textSizeSp, metrics)
    private val ink = InkStyle.of(context).foreground
    private val sample = ScreenTimeView.Usage("1 ч 20", "3 ч 05", 0.3f, 12)

    @Test fun aWideTileTooShortForFullTextKeepsTheNamesRatherThanShrinkFurtherInStacks() {
        val view = ScreenTimeView(context).apply { showUsage(sample) }
        render(view, 1000, 2 * padding + (fullSizePx * 2.8f).roundToInt())
        assertFalse(view.isCompact)
    }

    @Test fun aTileTooSmallForItsContentStillDrawsItsBar() {
        val view = ScreenTimeView(context).apply { showUsage(sample) }
        assertTrue(render(view, 2 * padding + 3, 2 * padding + 10).pixels().any { it == ink })
    }
}
