package io.github.andreygladkov.ink

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.createBitmap
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertTrue

const val A9_QUALIFIERS = "ru-w366dp-h732dp-360dpi"
const val A9_WIDTH = 824
const val A9_HEIGHT = 1648

fun contentRoot(activity: Activity): ViewGroup =
    activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup

fun render(view: View, width: Int = A9_WIDTH, height: Int = A9_HEIGHT): Bitmap {
    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
    view.layout(0, 0, width, height)
    return createBitmap(width, height).also { view.draw(Canvas(it)) }
}

fun Bitmap.pixels() = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

fun assertPureBlackAndWhite(bitmap: Bitmap) {
    val other = bitmap.pixels().filter { it != Color.BLACK && it != Color.WHITE }.distinct()
    assertTrue(other.isEmpty(), "found non b/w colors: ${other.take(5).joinToString { Integer.toHexString(it) }}")
}

inline fun <reified T : View> View.find(): List<T> = descendants(this).filterIsInstance<T>()

fun descendants(view: View): List<View> =
    listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

fun idleUntil(timeoutMs: Long = 5000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        check(System.currentTimeMillis() < deadline) { "condition not met in $timeoutMs ms" }
        Thread.sleep(10)
    }
}
