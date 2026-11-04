package io.github.andreygladkov.inkmessages.thread

import android.content.Context
import android.telephony.SmsMessage
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.inkmessages.R
import io.github.andreygladkov.inkmessages.sms.Sim
import io.github.andreygladkov.inkmessages.sms.SmsCounter
import kotlin.math.roundToInt

class ComposeBar(context: Context, private val onSend: (String) -> Unit) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val simRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        visibility = GONE
    }
    private val counter = InkTextView(context).apply {
        textScale = InkStyle.SECONDARY_SCALE
        gravity = Gravity.END
        visibility = GONE
    }
    val field = InkEditText(context).apply {
        isBordered = true
        isSingleLine = false
        maxLines = MAX_LINES
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        contentDescription = context.getString(R.string.message_field)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateCounter()
        })
    }
    private val send = iconButton(context, R.drawable.ic_send, context.getString(R.string.send)).apply {
        setOnClickListener {
            val text = field.text.toString()
            if (text.isNotBlank()) onSend(text)
        }
    }

    var sim: Sim? = null
        private set

    init {
        orientation = VERTICAL
        val gap = dp(GAP_DP)
        setPadding(gap, gap, gap, gap)
        addView(simRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = gap })
        addView(counter, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = gap })
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(field, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(send, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        })
    }

    fun setSims(sims: List<Sim>, selected: Sim?, onChoose: (Sim) -> Unit) {
        sim = selected
        simRow.removeAllViews()
        simRow.visibility = if (sims.size > 1) VISIBLE else GONE
        if (sims.size < 2) return
        val buttons = sims.map { option ->
            InkButton(context).apply {
                text = context.getString(R.string.sim_choice, option.slot + 1, option.label).trim()
                textScale = InkStyle.SECONDARY_SCALE
                maxLines = 1
                isSelected = option == selected
            } to option
        }
        buttons.forEachIndexed { index, (button, option) ->
            button.setOnClickListener {
                sim = option
                buttons.forEach { (other, otherOption) -> other.isSelected = otherOption == option }
                onChoose(option)
            }
            simRow.addView(button, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { if (index > 0) marginStart = dp(GAP_DP) })
        }
    }

    fun clear() = field.setText("")

    private fun updateCounter() {
        val text = field.text ?: ""
        val (parts, _, remaining) = SmsMessage.calculateLength(text, false)
        val show = SmsCounter.isWorthShowing(text, parts, remaining)
        counter.visibility = if (show) VISIBLE else GONE
        if (show) counter.text = context.getString(R.string.sms_counter, parts, remaining)
    }

    private operator fun IntArray.component1() = this[0]
    private operator fun IntArray.component2() = this[1]
    private operator fun IntArray.component3() = this[2]

    private fun dp(value: Float) = (value * density).roundToInt()

    private companion object {
        const val MAX_LINES = 5
        const val GAP_DP = 8f
    }
}
