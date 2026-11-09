package io.github.andreygladkov.ink.ui

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout

/** A nested screen of rows under a header, paged or scrolled like the app's lists, that keeps its place when recreated. */
abstract class InkPagedActivity : Activity() {
    private lateinit var list: InkPagedList
    private lateinit var items: InkPagedItems
    private var shownState: Any? = null

    protected abstract fun headerTitle(): String

    protected abstract fun InkPagedItems.build()

    /** What the rows show from outside the app's preferences; the screen is built again on return only when it changed. */
    protected open fun outsideState(): Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A page shrunk for the keyboard is laid out again with new views, and the field being typed into loses
        // focus, so the window pans to the field instead.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        list = InkPagedList(this, dividers = false)
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkHeader.of(this@InkPagedActivity, headerTitle(), ::finish))
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }, padForKeyboard = false)
        shownState = outsideState()
        items = items()
        list.setAdapter(items, savedInstanceState?.getString(STATE_KEY)?.let(items::positionOf) ?: 0)
    }

    override fun onResume() {
        super.onResume()
        rebuildIfChanged()
    }

    protected fun rebuildIfChanged() {
        if (outsideState() != shownState) rebuild()
    }

    protected fun rebuild() {
        shownState = outsideState()
        val key = items.keyAt(list.firstPosition)
        items = items()
        list.replaceAdapter(items, key?.let(items::positionOf) ?: list.firstPosition.coerceAtMost(items.count - 1).coerceAtLeast(0))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        items.keyAt(list.firstPosition)?.let { outState.putString(STATE_KEY, it) }
    }

    private fun items() = InkPagedItems(this).apply { build() }

    private companion object {
        const val STATE_KEY = "paged_key"
    }
}
