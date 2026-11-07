package io.github.andreygladkov.inklauncher.ui

import android.view.View
import android.view.WindowInsets
import androidx.core.view.doOnAttach

/**
 * Moves the view's content above the keyboard. The home grid stays where it is instead: resizing it would
 * render every icon and widget again and redraw the whole panel, twice, for one rename.
 */
fun View.keepAboveKeyboard() {
    val bottom = paddingBottom
    setOnApplyWindowInsetsListener { view, insets ->
        val keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom - insets.getInsets(WindowInsets.Type.systemBars()).bottom
        view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottom + keyboard.coerceAtLeast(0))
        insets
    }
    doOnAttach { it.requestApplyInsets() }
}
