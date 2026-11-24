package io.github.andreygladkov.inktasks.lists

import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.widget.LinearLayout
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkPagedActivity
import io.github.andreygladkov.ink.ui.InkPagedItems
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.inktasks.R
import io.github.andreygladkov.inktasks.store.TaskLists
import java.text.Collator

class ListsActivity : InkPagedActivity() {
    private val lists get() = TaskLists.of(this)

    override fun headerTitle(): String = getString(R.string.lists)

    override fun outsideState(): Any = lists.names() to lists.current

    override fun InkPagedItems.build() {
        val current = lists.current
        val collator = Collator.getInstance()
        (lists.names() + current).distinct().sortedWith { a, b -> collator.compare(a, b) }.forEach { name ->
            item { row(name, isCurrent = name == current) }
        }
        item {
            InkSettings.rowButton(this@ListsActivity, getString(R.string.new_list)) { startActivity(ListEditActivity.create(this@ListsActivity)) }.apply {
                icon = R.drawable.ic_add
                iconPosition = InkButton.IconPosition.START
            }
        }
    }

    private fun row(name: String, isCurrent: Boolean) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(InkButton(this@ListsActivity).apply {
            text = name
            isBold = isCurrent
            isBordered = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener {
                lists.open(name)
                finish()
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(iconButton(this@ListsActivity, R.drawable.ic_edit, getString(R.string.edit_list, name)).apply {
            setOnClickListener { startActivity(ListEditActivity.edit(this@ListsActivity, name)) }
        })
    }

    companion object {
        fun intent(context: Context) = Intent(context, ListsActivity::class.java)
    }
}
