package io.github.andreygladkov.inklauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.text.TextUtils
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import io.github.andreygladkov.ink.eink.MonochromeIcons
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.inklauncher.R
import kotlin.math.roundToInt

class SheetAction(context: Context, private val insetPx: Int) : InkButton(context) {
    private val density = resources.displayMetrics.density
    private val checkPx = (CHECK_DP * density).roundToInt()
    private var isChoice = false

    var isChecked = false
        set(value) {
            field = value
            isChoice = true
            isBold = value
            setPaddingRelative(insetPx, paddingTop, if (value) insetPx + checkPx + insetPx / 2 else insetPx, paddingBottom)
            invalidate()
        }

    init {
        isBordered = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        minHeight = (MIN_HEIGHT_DP * density).roundToInt()
        paddingDp = VERTICAL_PADDING_DP
        setPaddingRelative(insetPx, paddingTop, insetPx, paddingBottom)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (!isChoice) return
        info.isCheckable = true
        info.isChecked = isChecked
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isChecked) return
        val check = MonochromeIcons.bitmap(context, R.drawable.ic_check, checkPx, textColor)
        val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) insetPx else width - insetPx - checkPx
        canvas.drawBitmap(check, left.toFloat(), ((height - checkPx) / 2).toFloat(), null)
    }

    private companion object {
        const val MIN_HEIGHT_DP = 48f
        const val VERTICAL_PADDING_DP = 8f
        const val CHECK_DP = 24f
    }
}
