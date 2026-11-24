package io.github.andreygladkov.inktasks.ui

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.inktasks.R
import io.github.andreygladkov.inktasks.tasks.Task
import kotlin.math.roundToInt

class TaskRows(
    private val context: Context,
    private val labels: DueLabels,
    private val onCheck: (Task) -> Unit,
    private val onOpen: (Task) -> Unit,
) {
    private val style = InkStyle.of(context)

    fun row(task: Task, isSubtask: Boolean = false, isFramed: Boolean = false, detail: String? = null): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(if (isSubtask) SUBTASK_INDENT_DP else 0f), 0, 0, 0)
        addView(box(task, isSubtask))
        addView(InkButton(context).apply {
            text = text(task, detail)
            isBordered = isFramed
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = MAX_LINES
            ellipsize = TextUtils.TruncateAt.END
            setOnClickListener { onOpen(task) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun box(task: Task, isSubtask: Boolean) = InkButton(context).apply {
        icon = if (task.isDone) R.drawable.ic_check_box else R.drawable.ic_check_box_outline
        iconSizeDp = if (isSubtask) SUBTASK_BOX_DP else BOX_DP
        isBordered = false
        minWidth = dp(InkButton.MIN_HEIGHT_DP)
        contentDescription = context.getString(if (task.isDone) R.string.uncheck else R.string.check, task.title)
        setOnClickListener { onCheck(task) }
    }

    /** Overdue is the one thing that must stand out on a page of tasks, so its date is drawn white on black. */
    private fun text(task: Task, detail: String?): CharSequence {
        val text = SpannableStringBuilder(task.title)
        val note = task.notes.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        val due = task.due?.takeIf { !task.isDone }
        val small = listOfNotNull(note, detail)
        if (small.isEmpty() && due == null) return text
        text.append('\n')
        val start = text.length
        text.append(small.joinToString("\n"))
        if (due != null) {
            if (small.isNotEmpty()) text.append('\n')
            val overdue = labels.isOverdue(due)
            val from = text.length
            text.append(if (overdue) " ${labels.day(due)} " else labels.day(due))
            if (overdue) {
                text.setSpan(BackgroundColorSpan(style.foreground), from, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.setSpan(ForegroundColorSpan(style.background), from, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        text.setSpan(RelativeSizeSpan(InkStyle.SECONDARY_SCALE), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text
    }

    private fun dp(value: Float) = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val BOX_DP = 28f
        const val SUBTASK_BOX_DP = 24f
        const val SUBTASK_INDENT_DP = 40f
        const val MAX_LINES = 5
    }
}
