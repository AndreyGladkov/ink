package io.github.andreygladkov.ink.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import androidx.annotation.DrawableRes
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.eink.MonochromeIcons
import io.github.andreygladkov.ink.eink.StemFit
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.haptics.Haptics
import kotlin.math.roundToInt

open class InkButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : InkTextView(context, attrs) {
    enum class IconPosition { TOP, START }

    var hapticChannel: HapticChannel = HapticChannel.BUTTONS
    var isBordered: Boolean = true
        set(value) {
            field = value
            invalidate()
        }
    var isFillRounded: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    @DrawableRes
    var icon: Int = 0
        set(value) {
            field = value
            updateIconPadding()
            invalidate()
        }

    var iconPosition: IconPosition = IconPosition.TOP
        set(value) {
            field = value
            updateIconPadding()
            invalidate()
        }

    var iconSizeDp: Float = ICON_DP
        set(value) {
            field = value
            updateIconPadding()
            invalidate()
        }

    var badge: String? = null
        set(value) {
            field = value
            invalidate()
        }

    var cornerRadiusDp: Float? = null
        set(value) {
            field = value
            invalidate()
        }

    private val haptics by lazy { Haptics.of(context) }
    private val density = resources.displayMetrics.density
    private val fill = Paint().apply { isAntiAlias = false }
    private val borderPx = (InkStyle.LINE_DP * density).roundToInt().coerceAtLeast(1)
    var paddingDp: Float = PADDING_DP
        set(value) {
            field = value
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            updateIconPadding()
            invalidate()
        }
    private val paddingPx get() = (paddingDp * density).roundToInt()
    private val iconGapPx = (ICON_GAP_DP * density).roundToInt()
    private val iconSizePx get() = (iconSizeDp * density).roundToInt()
    private val radiusPx get() = ((cornerRadiusDp ?: style.cornerRadiusDp) * density).roundToInt().toFloat()

    private val hasLabel get() = icon != 0 && !text.isNullOrEmpty()

    private val isInverted get() = isPressed || isSelected

    override val textColor: Int get() = if (isInverted) style.background else style.foreground

    init {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        minHeight = (MIN_HEIGHT_DP * density).roundToInt()
        background = null
    }

    override fun setPressed(pressed: Boolean) {
        if (pressed && !isPressed && isEnabled && isClickable) haptics.perform(hapticChannel)
        super.setPressed(pressed)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        refreshTextColor()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = if (isBordered || isFillRounded) radiusPx else 0f
        fill.color = style.foreground
        if (isInverted) {
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, fill)
        } else if (isBordered) {
            val b = borderPx.toFloat()
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, fill)
            fill.color = style.background
            val inner = (radius - b).coerceAtLeast(0f)
            canvas.drawRoundRect(b, b, w - b, h - b, inner, inner, fill)
        }
        if (icon != 0) drawIcon(canvas)
        super.onDraw(canvas)
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        updateIconPadding()
    }

    private fun updateIconPadding() {
        if (paddingPx == 0) return
        val iconSpace = iconSizePx + iconGapPx
        val top = if (hasLabel && iconPosition == IconPosition.TOP) paddingPx + iconSpace else paddingPx
        val start = when {
            hasLabel && iconPosition == IconPosition.START -> paddingPx + iconSpace
            paddingLeft == paddingPx + iconSpace -> paddingPx
            else -> paddingLeft
        }
        if (top != paddingTop || start != paddingLeft) setPadding(start, top, paddingRight, paddingBottom)
        minWidth = if (icon != 0) iconSizePx + 2 * paddingPx else 0
    }

    private fun drawIcon(canvas: Canvas) {
        val size = iconSizePx
        val bitmap = MonochromeIcons.bitmap(context, icon, size, textColor)
        val (left, top) = when {
            !hasLabel -> (width - size) / 2 to (height - size) / 2
            iconPosition == IconPosition.TOP -> (width - size) / 2 to paddingPx
            else -> {
                val textLeft = totalPaddingLeft + (layout?.getLineLeft(0)?.roundToInt() ?: 0)
                (textLeft - size - iconGapPx).coerceAtLeast(paddingPx) to (height - size) / 2
            }
        }
        canvas.drawBitmap(bitmap, left.toFloat(), top.toFloat(), null)
        badge?.let { drawBadge(canvas, it, left + size, top) }
    }

    private fun drawBadge(canvas: Canvas, label: String, centerX: Int, centerY: Int) {
        val radius = BADGE_RADIUS_DP * density
        val labelColor = if (isInverted) style.foreground else style.background
        val text = Paint().apply {
            isAntiAlias = false
            textSize = radius * BADGE_TEXT_TO_RADIUS
            typeface = StemFit.typeface(textSize, InkStyle.BOLD_WEIGHT)
            textAlign = Paint.Align.CENTER
            color = labelColor
        }
        val width = maxOf(radius * 2, text.measureText(label) + radius)
        fill.color = textColor
        canvas.drawRoundRect(centerX - width / 2, centerY - radius, centerX + width / 2, centerY + radius, radius, radius, fill)
        val baseline = centerY - (text.ascent() + text.descent()) / 2
        canvas.drawText(label, centerX.toFloat(), baseline.roundToInt().toFloat(), text)
    }

    companion object {
        const val PADDING_DP = 12f
        const val MIN_HEIGHT_DP = 56f
        private const val ICON_DP = 32f
        private const val ICON_GAP_DP = 4f
        private const val BADGE_RADIUS_DP = 10f
        private const val BADGE_TEXT_TO_RADIUS = 1.4f
    }
}
