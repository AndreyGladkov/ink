package io.github.andreygladkov.inkphone

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

enum class Tab(@param:StringRes val title: Int, @param:DrawableRes val icon: Int) {
    FAVORITES(R.string.tab_favorites, R.drawable.ic_tab_favorites),
    CALL_LOG(R.string.tab_call_log, R.drawable.ic_tab_call_log),
    CONTACTS(R.string.tab_contacts, R.drawable.ic_tab_contacts),
    DIALER(R.string.tab_dialer, R.drawable.ic_tab_dialer),
}
