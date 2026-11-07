package io.github.andreygladkov.inkcalculator

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Toast
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.inkcalculator.calc.Arithmetic
import io.github.andreygladkov.inkcalculator.calc.CalculatorStore
import io.github.andreygladkov.inkcalculator.calc.DisplayFormat
import io.github.andreygladkov.inkcalculator.calc.Entry
import io.github.andreygladkov.inkcalculator.calc.OPERATORS
import io.github.andreygladkov.inkcalculator.calc.Outcome
import io.github.andreygladkov.inkcalculator.calc.TapeLine
import io.github.andreygladkov.inkcalculator.ui.BottomUpColumn
import io.github.andreygladkov.inkcalculator.ui.CalculatorKeypad
import io.github.andreygladkov.inkcalculator.ui.FitTextView
import io.github.andreygladkov.inkcalculator.ui.Key
import kotlin.math.roundToInt

class CalculatorScreen(private val activity: Activity) : LinearLayout(activity) {
    private val store = CalculatorStore.of(activity)
    private val format = DisplayFormat.of(resources.configuration.locales[0])
    private var entry = store.entry
    private var message: Int? = null

    private val tape = BottomUpColumn(activity)
    private val expression = FitTextView(activity, EXPRESSION_SCALES).apply {
        isBold = true
        gravity = Gravity.END
        setOnLongClickListener { copy() }
    }
    private val preview = FitTextView(activity, PREVIEW_SCALES).apply {
        gravity = Gravity.END
        maxLines = 1
    }

    init {
        orientation = VERTICAL
        val padding = dp(PADDING_DP)
        setPadding(padding, 0, padding, padding)
        addView(tape, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(expression, wide())
        addView(preview, wide())
        addView(CalculatorKeypad(activity, format.decimalSeparator, ::press), wide().apply { topMargin = dp(GAP_DP) })
        showTape()
        show()
    }

    private fun press(key: Key) {
        message = null
        entry = when (key) {
            is Key.Digits -> entry.digits(key.keys)
            is Key.Operator -> entry.operator(key.symbol)
            Key.Point -> entry.point()
            Key.Percent -> entry.percent()
            Key.Erase -> entry.erase()
            Key.Clear -> Entry()
            Key.Equals -> calculated()
        }
        store.entry = entry
        show()
    }

    private fun calculated(): Entry {
        if (!entry.isCalculation || entry.isResult) return entry
        return when (val outcome = entry.evaluate()) {
            is Outcome.Value -> Entry.result(outcome.number).also { result ->
                store.remember(TapeLine(entry.text.trimEnd { it in OPERATORS }, result.text))
                showTape()
            }
            Outcome.DivisionByZero -> entry.also { message = R.string.division_by_zero }
            Outcome.TooLarge -> entry.also { message = R.string.too_large }
            Outcome.Invalid -> entry
        }
    }

    private fun show() {
        expression.text = format.expression(entry.text).ifEmpty { "0" }
        val message = message
        val value = (entry.evaluate() as? Outcome.Value)?.takeIf { entry.isCalculation && !entry.isResult }
        preview.text = when {
            message != null -> activity.getString(message)
            value != null -> "= ${format.number(Arithmetic.shown(value.number))}"
            else -> ""
        }
    }

    private fun showTape() {
        tape.removeAllViews()
        store.tape().forEach { line ->
            tape.addView(InkButton(activity).apply {
                text = "${format.expression(line.expression)} = ${format.expression(line.result)}"
                textScale = InkStyle.SECONDARY_SCALE
                isBordered = false
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.START
                minHeight = dp(TAPE_LINE_DP)
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    message = null
                    entry = entry.insert(line.result)
                    store.entry = entry
                    show()
                }
            })
        }
    }

    private fun copy(): Boolean {
        val value = entry.evaluate() as? Outcome.Value ?: return false
        val text = format.copyable(Arithmetic.shown(value.number))
        activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(text, text))
        // Android 13 confirms a copy itself.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(activity, R.string.copied, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun wide() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        val EXPRESSION_SCALES = listOf(2.5f, 2f, 1.6f, 1.3f)
        val PREVIEW_SCALES = listOf(InkStyle.DISPLAY_SCALE, InkStyle.TITLE_SCALE, 1f)
        const val PADDING_DP = 16f
        const val GAP_DP = 8f
        const val TAPE_LINE_DP = 48f
    }
}
