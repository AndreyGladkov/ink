package io.github.andreygladkov.ink.eink

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.RegionIterator
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.edit
import io.github.andreygladkov.ink.lockSafeStorage
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Without antialiasing a stem covers as many pixels as pixel centers it contains, so a 5.5 px stem is drawn
 * 5 or 6 px wide depending on its position and the two stems of "П" differ. A stem of integral width always
 * covers the same number of pixels, so this picks the weight axis value closest to the wanted weight at which
 * the stem width at the given size is closest to an integer. System Roboto is a variable font without hinting
 * instructions, so the axis is the only way to snap stems; Typeface.create only picks named instances.
 *
 * Unhinted outlines scale linearly with the text size, so stem widths are measured once per weight at a
 * reference size and kept across launches; measuring them per size took ~380 ms each on the Hisense A9.
 */
object StemFit {
    private const val FIT_RANGE = 80
    private const val FIT_STEP = 5
    private const val TOLERANCE = 0.02f
    // Searching outward from the wanted weight and stopping at the first close fit keeps the look of the
    // chosen weight; the globally best fit can be 70 units lighter and visibly thinner.
    private const val GOOD_ENOUGH = 0.05f
    private const val REFERENCE_PX = 1000f
    private const val STEM_ROW = 0.3f
    private const val UNSUPPORTED = "-"
    private val REFERENCES = listOf("l", "п", "н", "т")

    private data class Key(val textSizePx: Float, val weight: Int)
    private val typefaces = HashMap<Key, Typeface>()
    private val unitStems = HashMap<Int, FloatArray?>()
    private var store: SharedPreferences? = null

    fun attach(context: Context) {
        store = context.lockSafeStorage().getSharedPreferences("stem_fit", Context.MODE_PRIVATE)
    }

    fun typeface(textSizePx: Float, weight: Int): Typeface =
        synchronized(this) { typefaces.getOrPut(Key(textSizePx, weight)) { fit(textSizePx, weight) } }

    private fun fit(textSizePx: Float, wanted: Int): Typeface {
        var best: Int? = null
        var bestError = Float.MAX_VALUE
        for (offset in 0..FIT_RANGE step FIT_STEP) {
            for (weight in listOf(wanted - offset, wanted + offset).distinct()) {
                if (weight !in 100..900) continue
                val stems = stemsPerPx(weight)
                if (stems == null || stems.isEmpty()) return Typeface.create(Typeface.DEFAULT, wanted, false)
                val error = stems.sumOf { stem -> (stem * textSizePx).let { abs(it - it.roundToInt()) }.toDouble() }.toFloat() / stems.size
                if (error <= GOOD_ENOUGH) return withWeight(weight, wanted)
                if (error < bestError - TOLERANCE) {
                    best = weight
                    bestError = error
                }
            }
        }
        return withWeight(best, wanted)
    }

    private fun withWeight(weight: Int?, wanted: Int): Typeface = Paint().apply {
        typeface = Typeface.DEFAULT
        fontVariationSettings = weight?.let { "'wght' $it" }
    }.typeface ?: Typeface.create(Typeface.DEFAULT, wanted, false)

    private fun stemsPerPx(weight: Int): FloatArray? = unitStems.getOrPut(weight) {
        val key = "${Build.FINGERPRINT}|$weight"
        val saved = store?.getString(key, null)
        if (saved != null) {
            if (saved == UNSUPPORTED) null else saved.split(',').filter { it.isNotEmpty() }.map(String::toFloat).toFloatArray()
        } else {
            measure(weight).also { stems ->
                store?.edit { putString(key, stems?.joinToString(",") ?: UNSUPPORTED) }
            }
        }
    }

    private fun measure(weight: Int): FloatArray? {
        val probe = Paint().apply {
            typeface = Typeface.DEFAULT
            textSize = REFERENCE_PX
        }
        if (!probe.setFontVariationSettings("'wght' $weight")) return null
        return REFERENCES.flatMap { stemWidths(probe, it) }.map { it / REFERENCE_PX }.toFloatArray()
    }

    // Widths of the runs a horizontal line crosses in the upper part of the glyph, which for these
    // references are the vertical stems.
    private fun stemWidths(probe: Paint, text: String): List<Float> {
        val path = Path()
        probe.getTextPath(text, 0, text.length, 0f, 0f, path)
        val bounds = RectF()
        path.computeBounds(bounds, true)
        val y = (bounds.top + bounds.height() * STEM_ROW).toInt()
        val region = Region()
        region.setPath(path, Region(bounds.left.toInt() - 1, y, bounds.right.toInt() + 1, y + 1))
        val widths = ArrayList<Float>()
        val iterator = RegionIterator(region)
        val rect = Rect()
        while (iterator.next(rect)) widths.add(rect.width().toFloat())
        return widths
    }
}
