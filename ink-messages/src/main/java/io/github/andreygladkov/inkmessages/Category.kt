package io.github.andreygladkov.inkmessages

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.andreygladkov.ink.ui.InkTab
import io.github.andreygladkov.inkmessages.conversations.Conversation

enum class Category(
    @StringRes override val title: Int,
    @DrawableRes override val icon: Int,
    @StringRes val empty: Int,
) : InkTab {
    FAVORITES(R.string.category_favorites, R.drawable.ic_tab_favorites, R.string.empty_favorites),
    CONTACTS(R.string.category_contacts, R.drawable.ic_tab_contacts, R.string.empty_contacts),
    OTHER(R.string.category_other, R.drawable.ic_tab_other, R.string.empty_other),
    ALL(R.string.category_all, R.drawable.ic_tab_all, R.string.empty_all);

    fun includes(conversation: Conversation): Boolean = when (this) {
        FAVORITES -> conversation.participants.any { it.isStarred }
        CONTACTS -> conversation.participants.any { it.isContact }
        OTHER -> conversation.participants.none { it.isContact }
        ALL -> true
    }
}
