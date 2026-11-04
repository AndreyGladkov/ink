package io.github.andreygladkov.ink.ui

import android.graphics.Paint
import android.util.TypedValue
import io.github.andreygladkov.ink.eink.StemFit
import kotlin.math.floor

private const val SCALE_STEPS = 20

// Quantized so that rows on different screens land on the same few sizes, each with its own cached StemFit typeface.
// Every candidate is measured with the typeface it will be drawn with: StemFit picks a different weight per size,
// so a width taken at the base size does not scale linearly.
fun fittedLabelScale(buttons: Collection<InkButton>, buttonWidthPx: Int, maxScale: Float): Float {
    val first = buttons.first()
    val baseSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, first.style.textSizeSp, first.resources.displayMetrics)
    val probe = Paint(first.paint)
    fun fits(scale: Float): Boolean {
        probe.textSize = baseSize * scale
        probe.typeface = StemFit.typeface(probe.textSize, first.style.weight)
        return buttons.all { probe.measureText(it.text.toString()) <= buttonWidthPx - it.paddingLeft - it.paddingRight }
    }
    val largest = floor(maxScale * SCALE_STEPS).toInt()
    val step = (largest downTo 1).firstOrNull { fits(it.toFloat() / SCALE_STEPS) } ?: 1
    return step.toFloat() / SCALE_STEPS
}
