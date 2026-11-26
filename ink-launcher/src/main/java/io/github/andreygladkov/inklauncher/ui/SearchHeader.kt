package io.github.andreygladkov.inklauncher.ui

import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
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
            override fun afterTextChanged(s: Editable?) {
                openedUnasked = false
                onQuery(query)
            }
        })
        setOnFocusChangeListener { _, focused -> onFieldFocus(focused) }
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
        setOnApplyWindowInsetsListener { _, insets -> insets.also { onKeyboard(it.isVisible(WindowInsets.Type.ime())) } }
    }

    private var keyboardShown = false

    /** Opened with the screen and not typed into since: Back leaves the screen instead of the search, keyboard or not. */
    var openedUnasked = false
        private set

    @Suppress("DEPRECATION")
    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK || !openedUnasked) return super.dispatchKeyEventPreIme(event)
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) activity.onBackPressed()
        return true
    }

    /** The system back hides the keyboard without taking the focus from the field. */
    private fun onKeyboard(shown: Boolean) {
        if (keyboardShown && !shown) queryField.clearFocus()
        keyboardShown = shown
    }

    private fun onFieldFocus(focused: Boolean) {
        if (!isSearching) return
        if (!focused && query.isEmpty) close() else showCloseButton(focused)
    }

    private fun showCloseButton(shown: Boolean) {
        closeButton.isVisible = shown
        openButton.isVisible = !shown
    }

    fun setTitle(title: String) {
        titleView.text = title
    }

    val isSearching: Boolean get() = queryField.isVisible

    val query: SearchQuery get() = SearchQuery(queryField.text.toString())

    /** False when there was no search to close. */
    fun close(): Boolean {
        if (!isSearching) return false
        openedUnasked = false
        queryField.visibility = GONE
        hideKeyboard()
        if (!queryField.text.isNullOrEmpty()) queryField.text = null
        showCloseButton(false)
        titleView.visibility = VISIBLE
        return true
    }

    fun hideKeyboard() {
        hideSoftInput()
        queryField.clearFocus()
    }

    private fun hideSoftInput() {
        activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onDetachedFromWindow() {
        hideSoftInput()
        super.onDetachedFromWindow()
    }

    fun open(asked: Boolean = true) {
        openedUnasked = !asked
        titleView.visibility = GONE
        queryField.visibility = VISIBLE
        showCloseButton(true)
        activity.showKeyboard(queryField)
    }
}
