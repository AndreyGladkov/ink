package io.github.andreygladkov.inkgallery.grid

import android.app.Activity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.isInvisible
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.BackToStart
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.inkgallery.R
import io.github.andreygladkov.inkgallery.viewer.ActionPanel
import io.github.andreygladkov.inkgallery.viewer.Actions

/**
 * Picking items of [grid]: over [header] how many are picked and Cancel, at the bottom Share and Delete for all of
 * them, the same row as under a photo. Back cancels too, and a deletion the system confirms ends the picking and
 * calls [onDeleted]. Everything shown for picking is laid over what it replaces, and what is hidden keeps its room,
 * so the grid keeps its size and its pages do not shift when picking starts.
 */
class GridSelection(
    private val activity: Activity,
    private val grid: PhotoGrid,
    private val header: View,
    private val footer: View?,
    private val onDeleted: () -> Unit,
) {
    private val title = InkHeader.title(activity, "")
    private val back = BackToStart(activity) { grid.clearSelection() }
    private val pageColor = InkStyle.of(activity).background

    private val bar: View = InkHeader.of(
        activity, title,
        InkHeader.button(activity, R.drawable.ic_close, activity.getString(R.string.cancel)) { grid.clearSelection() },
    ).apply {
        setBackgroundColor(pageColor)
        isInvisible = true
    }

    private val actions = ActionPanel(activity).apply {
        add(R.drawable.ic_share, R.string.share) { Actions.share(activity, grid.selection) }
        addDelete({ activity.getString(R.string.delete_picked_question, grid.selection.size) }) {
            Actions.askToDelete(activity, grid.selection)
        }
    }

    private val actionsWithLine: View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(pageColor)
        isInvisible = true
        addView(InkHeader.line(activity))
        addView(actions, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    val top: View = FrameLayout(activity).apply {
        addView(header, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(bar, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    /** The footer with the actions laid over it, or without a footer the actions alone, to lay over the grid's bottom. */
    val bottom: View = if (footer == null) actionsWithLine else FrameLayout(activity).apply {
        addView(footer, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(actionsWithLine, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    init {
        grid.onSelectionChanged = ::show
    }

    /** True when the press only ended the picking. */
    fun onBackPressed() = back.onBackPressed()

    /** True when [requestCode] was the picked items' deletion. */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != Actions.REQUEST_DELETE) return false
        if (resultCode == Activity.RESULT_OK) {
            grid.clearSelection()
            onDeleted()
        }
        return true
    }

    private fun show() {
        val picking = grid.isSelecting
        back.isAway = picking
        bar.isInvisible = !picking
        header.isInvisible = picking
        actionsWithLine.isInvisible = !picking
        footer?.isInvisible = picking
        title.text = activity.getString(R.string.selected_count, grid.selection.size)
        actions.cancelQuestion()
    }
}
