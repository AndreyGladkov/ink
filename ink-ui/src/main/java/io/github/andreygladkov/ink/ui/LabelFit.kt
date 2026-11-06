package io.github.andreygladkov.ink.ui

import android.graphics.Paint
import android.util.TypedValue
import io.github.andreygladkov.ink.eink.StemFit
import kotlin.math.floor

private const val SCALE_STEPS = 20

/**
 * Measured with the typeface the text will be drawn with: StemFit picks a different weight per size, so a width
 * taken at the base size does not scale linearly.
 */
fun InkTextView.textWidthAtScale(text: CharSequence, scale: Float, probe: Paint = Paint(paint)): Float {
    probe.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp * scale, resources.displayMetrics)
    probe.typeface = StemFit.typeface(probe.textSize, drawnWeight)
    return probe.measureText(text.toString())
}

// Quantized so that rows on different screens land on the same few sizes, each with its own cached StemFit typeface.
fun fittedLabelScale(buttons: Collection<InkButton>, buttonWidthPx: Int, maxScale: Float): Float {
    val probe = Paint(buttons.first().paint)
    fun fits(scale: Float) = buttons.all { it.textWidthAtScale(it.text, scale, probe) <= buttonWidthPx - it.paddingLeft - it.paddingRight }
    val largest = floor(maxScale * SCALE_STEPS).toInt()
    val step = (largest downTo 1).firstOrNull { fits(it.toFloat() / SCALE_STEPS) } ?: 1
    return step.toFloat() / SCALE_STEPS
}
