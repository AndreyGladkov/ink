package io.github.andreygladkov.inktasks.lists

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.confirmBeforeAction
import io.github.andreygladkov.inktasks.R
import io.github.andreygladkov.inktasks.store.ListName
import io.github.andreygladkov.inktasks.store.TaskLists
import kotlin.math.roundToInt

class ListEditActivity : Activity() {
    private val lists by lazy { TaskLists.of(this) }
    private var original: String? = null
    private lateinit var nameField: InkEditText
    private lateinit var error: InkTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        original = intent.getStringExtra(EXTRA_NAME)
        val ready = InkButton(this).apply {
            setText(R.string.ready)
            setOnClickListener { save() }
        }
        val title = original?.let { getString(R.string.list) } ?: getString(R.string.new_list)
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkHeader.of(this@ListEditActivity, title, ::finish, ready.apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = dp(PADDING_DP) }
            }))
            addView(body(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        nameField.requestFocus()
    }

    private fun body() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val padding = dp(PADDING_DP)
        setPadding(padding, padding, padding, padding)
        nameField = InkEditText(this@ListEditActivity).apply {
            isBordered = true
            hint = getString(R.string.list_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            filters = arrayOf(InputFilter { source, start, end, _, _, _ ->
                val typed = source.subSequence(start, end)
                ListName.strip(typed).takeIf { it.length != typed.length }
            })
            setText(original.orEmpty())
            setSelection(text.length)
            setOnEditorActionListener { _, _, _ ->
                save()
                true
            }
        }
        addView(nameField, wide())
        error = InkTextView(this@ListEditActivity).apply {
            setText(R.string.list_exists)
            textScale = InkStyle.SECONDARY_SCALE
            setPadding(0, dp(GAP_DP), 0, 0)
            visibility = View.INVISIBLE
        }
        addView(error, wide())
        val name = original ?: return@apply
        addView(InkChoiceRow(this@ListEditActivity, getString(R.string.sort),
            listOf(TaskLists.Sort.MANUAL to getString(R.string.sort_manual), TaskLists.Sort.DUE to getString(R.string.sort_due)),
            lists.sort(name)) { lists.setSort(name, it) }, wide())
        if (lists.exists(name)) addView(InkButton(this@ListEditActivity).apply {
            setText(R.string.delete_list)
            icon = R.drawable.ic_delete
            iconPosition = InkButton.IconPosition.START
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            confirmBeforeAction(R.string.delete_list_confirm) {
                lists.delete(name)
                finish()
            }
        }, wide().apply { topMargin = dp(SECTION_GAP_DP) })
    }

    /** The default list has no file until its first task, so renaming it creates the file under the new name. */
    private fun save() {
        val name = ListName.clean(nameField.text) ?: return
        val original = original
        val saved = when {
            original == name -> true
            original != null && lists.names().contains(original) -> lists.rename(original, name)
            else -> lists.create(name).also { if (it) lists.open(name) }
        }
        if (!saved) {
            error.visibility = View.VISIBLE
            return
        }
        finish()
    }

    private fun wide() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_NAME = "name"
        private const val PADDING_DP = 16f
        private const val GAP_DP = 8f
        private const val SECTION_GAP_DP = 24f

        fun create(context: Context) = Intent(context, ListEditActivity::class.java)

        fun edit(context: Context, name: String): Intent = create(context).putExtra(EXTRA_NAME, name)
    }
}
