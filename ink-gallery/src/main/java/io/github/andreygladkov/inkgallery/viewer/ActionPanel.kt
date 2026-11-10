package io.github.andreygladkov.inkgallery.viewer

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isInvisible
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkButtonRow
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkgallery.R
import kotlin.math.roundToInt

/**
 * The row of labelled actions at the bottom of a photo, a video or the picked items of a grid. Delete asks on the
 * spot: the row gives way to the question with OK and Cancel, so nothing pops up over the photo. The row keeps its
 * room while the question is shown, so the panel does not change height.
 */
class ActionPanel(context: Context) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val actions = InkButtonRow(context, 0, InkStyle.SECONDARY_SCALE)
    private val questionText = InkTextView(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        val padding = (QUESTION_PADDING_DP * density).roundToInt()
        setPadding(padding, 0, padding, 0)
    }
    private var onConfirm: (() -> Unit)? = null
    private val question = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isInvisible = true
        addView(questionText, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
        addView(InkButtonRow(context, (ANSWER_GAP_DP * density).roundToInt(), 1f, isDivided = true).apply {
            addButton(answer(R.string.ok) {
                val confirmed = onConfirm
                cancelQuestion()
                confirmed?.invoke()
            })
            addButton(answer(R.string.cancel, ::cancelQuestion))
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    }

    init {
        setBackgroundColor(InkStyle.of(context).background)
        isClickable = true
        addView(actions, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(question, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    fun add(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit): InkButton =
        Overlay.action(context, icon, label, onClick).also(actions::addButton)

    /** Delete, which first asks [question] and acts only on OK. */
    fun addDelete(question: () -> String, onDelete: () -> Unit): InkButton = add(R.drawable.ic_delete, R.string.delete) {
        ask(question(), onDelete)
    }

    fun cancelQuestion() {
        onConfirm = null
        question.isInvisible = true
        actions.isInvisible = false
    }

    private fun ask(text: String, onYes: () -> Unit) {
        questionText.text = text
        onConfirm = onYes
        actions.isInvisible = true
        question.isInvisible = false
    }

    private fun answer(@StringRes label: Int, onClick: () -> Unit) = InkButton(context).apply {
        setText(label)
        isBordered = false
        setOnClickListener { onClick() }
    }

    private companion object {
        const val QUESTION_PADDING_DP = 16f
        const val ANSWER_GAP_DP = 8f
    }
}
