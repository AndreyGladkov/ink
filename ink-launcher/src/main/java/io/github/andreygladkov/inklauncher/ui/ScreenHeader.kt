package io.github.andreygladkov.inklauncher.ui

import android.content.Context
import io.github.andreygladkov.ink.ui.InkButton

interface SideScreen {
    fun refresh() = Unit

    /** True when the screen used the back press itself. */
    fun onBack(): Boolean = false
}

/** Sized like the page buttons it stands next to. */
fun footerButton(context: Context, icon: Int, description: Int, onClick: () -> Unit) = InkButton(context).apply {
    this.icon = icon
    contentDescription = context.getString(description)
    isBordered = false
    setOnClickListener { onClick() }
}
