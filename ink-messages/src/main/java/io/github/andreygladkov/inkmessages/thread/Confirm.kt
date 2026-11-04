package io.github.andreygladkov.inkmessages.thread

import androidx.annotation.StringRes
import io.github.andreygladkov.ink.ui.InkButton

/** There is no room for dialogs on e-ink: a destructive button asks for a second tap by changing its label. */
fun InkButton.confirmBeforeAction(@StringRes confirm: Int, action: () -> Unit) {
    var armed = false
    setOnClickListener {
        if (armed) action() else {
            armed = true
            setText(confirm)
        }
    }
}
