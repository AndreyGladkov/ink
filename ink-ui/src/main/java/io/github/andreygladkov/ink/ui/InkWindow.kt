package io.github.andreygladkov.ink.ui

import android.app.Activity
import android.view.View
import android.view.WindowInsets
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import io.github.andreygladkov.ink.eink.InkStyle

object InkWindow {
    fun setContent(activity: Activity, root: View) {
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
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
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
