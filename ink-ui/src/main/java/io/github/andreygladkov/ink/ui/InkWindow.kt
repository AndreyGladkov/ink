package io.github.andreygladkov.ink.ui

import android.app.Activity
import android.view.View
import android.view.WindowInsets
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import io.github.andreygladkov.ink.eink.InkStyle

object InkWindow {
    /**
     * With [padForKeyboard] off the root keeps its size when the keyboard opens, and the views that take text
     * move up on their own: a screen that cannot afford a full-panel redraw for that does it this way.
     */
    fun setContent(activity: Activity, root: View, padForKeyboard: Boolean = true) {
        val style = InkStyle.of(activity)
        val window = activity.window
        window.setBackgroundDrawable(style.background.toDrawable())
        WindowCompat.enableEdgeToEdge(window)
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        root.setBackgroundColor(style.background)
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        root.setOnApplyWindowInsetsListener { view, insets ->
            val types = if (padForKeyboard) WindowInsets.Type.systemBars() or WindowInsets.Type.ime() else WindowInsets.Type.systemBars()
            val bars = insets.getInsets(types)
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            insets
        }
        activity.setContentView(root)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !style.inverted
            isAppearanceLightNavigationBars = !style.inverted
        }
    }
}
