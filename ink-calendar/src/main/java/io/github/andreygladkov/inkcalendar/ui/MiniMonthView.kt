package io.github.andreygladkov.inkcalendar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.haptics.Haptics
import io.github.andreygladkov.ink.ui.MonthGrid
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import kotlin.math.roundToInt

class MiniMonthView(context: Context, val month: YearMonth, private val today: LocalDate) : View(context) {
    private val painter = MiniMonthPainter(context)
    private val locale get() = resources.configuration.locales[0]
    private val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
    private val inset = (INSET_DP * resources.displayMetrics.density).roundToInt()
    private val haptics by lazy { Haptics.of(context) }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = MonthGrid.title(month, locale)
    }

    override fun setPressed(pressed: Boolean) {
        if (pressed && !isPressed) haptics.perform(HapticChannel.BUTTONS)
        super.setPressed(pressed)
    }

    override fun onDraw(canvas: Canvas) {
        painter.draw(canvas, month, today, Rect(inset, inset, width - inset, height - inset), name)
    }

    private companion object {
        const val INSET_DP = 6f
    }
}
