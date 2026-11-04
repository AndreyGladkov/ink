package io.github.andreygladkov.inkmessages.conversations

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkmessages.Category
import io.github.andreygladkov.inkmessages.Labels
import io.github.andreygladkov.inkmessages.MessageDates
import kotlin.math.roundToInt

class ConversationListScreen(
    context: Context,
    private val category: Category,
    private val labels: Labels,
    private val dates: MessageDates,
    private val open: (Conversation) -> Unit,
) : FrameLayout(context) {
    private val list = InkPagedList(context)
    private val empty = InkTextView(context).apply {
        gravity = Gravity.CENTER
        setText(category.empty)
        visibility = GONE
        val padding = (PADDING_DP * resources.displayMetrics.density).roundToInt()
        setPadding(padding, padding, padding, padding)
        setBackgroundColor(InkStyle.of(context).background)
    }

    init {
        addView(list)
        addView(empty)
    }

    fun show(conversations: List<Conversation>, now: Long) {
        val visible = conversations.filter(category::includes)
        val anchor = list.firstPosition.takeIf { it < visible.size } ?: 0
        list.setAdapter(object : InkPagedList.Adapter {
            override val count = visible.size
            override fun itemId(position: Int) = visible[position].threadId
            override fun createView(position: Int): View = row(visible[position], now)
        }, anchor)
        empty.visibility = if (visible.isEmpty()) VISIBLE else GONE
    }

    private fun row(conversation: Conversation, now: Long) = ConversationRow(context).apply {
        val name = labels.title(conversation.participants)
        title.text = if (conversation.unread > 0) "$name (${conversation.unread})" else name
        date.text = dates.listLabel(conversation.last.date, now)
        preview.text = labels.preview(conversation.last)
        isUnread = conversation.unread > 0
        setOnClickListener { open(conversation) }
    }

    private companion object {
        const val PADDING_DP = 24f
    }
}
