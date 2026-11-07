package io.github.andreygladkov.inklauncher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.TextUtils
import android.view.Gravity
import io.github.andreygladkov.ink.ui.InkButton
import kotlin.math.roundToInt

/**
 * A list row with an app's icon before its name. The paged list builds rows just to measure them, so the icon
 * is rendered only once the row is attached to be shown.
 */
class AppRow(context: Context, label: String, private val iconOf: (sizePx: Int) -> Bitmap) : InkButton(context) {
    private val density = resources.displayMetrics.density
    private val iconPx = (ICON_DP * density).roundToInt()
    private val iconPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }

    private var filterColor = 0
    private var filter: PorterDuffColorFilter? = null

    var iconBitmap: Bitmap? = null
        private set

    init {
        text = label
        isBordered = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        val padding = (PADDING_DP * density).roundToInt()
        setPadding(padding + iconPx + (GAP_DP * density).roundToInt(), paddingTop, padding, paddingBottom)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (iconBitmap == null) iconBitmap = iconOf(iconPx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = iconBitmap ?: return
        if (filter == null || filterColor != currentTextColor) {
            filterColor = currentTextColor
            filter = PorterDuffColorFilter(filterColor, PorterDuff.Mode.SRC_IN)
        }
        iconPaint.colorFilter = filter
        canvas.drawBitmap(bitmap, (PADDING_DP * density).roundToInt().toFloat(), ((height - bitmap.height) / 2).toFloat(), iconPaint)
    }

    private companion object {
        const val ICON_DP = 32f
        const val PADDING_DP = 12f
        const val GAP_DP = 12f
    }
}
