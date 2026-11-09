package io.github.andreygladkov.inklauncher.ui

import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.search.SearchQuery
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.inklauncher.HomeActivity
import io.github.andreygladkov.inklauncher.R

/** A screen title with a magnifier; the magnifier swaps the title for a queryField and reports every change of the query. */
class SearchHeader(
    private val activity: HomeActivity,
    title: String,
    @StringRes hint: Int,
    leading: View?,
    actions: List<View> = emptyList(),
    private val onQuery: (SearchQuery) -> Unit,
) : LinearLayout(activity) {
    private val titleView = InkHeader.title(activity, title)
    private val queryField = InkEditText(activity).apply {
        visibility = GONE
        this.hint = SpannableString(context.getString(hint)).apply {
            setSpan(RelativeSizeSpan(InkStyle.SECONDARY_SCALE), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setOnEditorActionListener { _, _, _ ->
            hideKeyboard()
            true
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = onQuery(query)
        })
    }
    private val openButton = InkHeader.button(activity, R.drawable.ic_search, activity.getString(hint)) { open() }
    private val closeButton = InkHeader.button(activity, R.drawable.ic_close, activity.getString(R.string.close_search)) { close() }.apply { visibility = GONE }

    init {
        orientation = VERTICAL
        val center = FrameLayout(activity).apply {
            addView(titleView)
            addView(queryField, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))
        }
        addView(InkHeader.of(activity, center, leading, openButton, closeButton, *actions.toTypedArray()))
    }

    fun setTitle(title: String) {
        titleView.text = title
    }

    val isSearching: Boolean get() = queryField.isVisible

    val query: SearchQuery get() = SearchQuery(queryField.text.toString())

    /** False when there was no search to close. */
    fun close(): Boolean {
        if (!isSearching) return false
        hideKeyboard()
        queryField.text = null
        queryField.visibility = GONE
        closeButton.visibility = GONE
        titleView.visibility = VISIBLE
        openButton.visibility = VISIBLE
        return true
    }

    fun hideKeyboard() {
        activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(queryField.windowToken, 0)
        queryField.clearFocus()
    }

    private fun open() {
        titleView.visibility = GONE
        openButton.visibility = GONE
        queryField.visibility = VISIBLE
        closeButton.visibility = VISIBLE
        activity.showKeyboard(queryField)
    }
}
