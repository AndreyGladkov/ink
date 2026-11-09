package io.github.andreygladkov.inklauncher.ui

import android.os.Build
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.annotation.StringRes
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.confirmBeforeAction
import io.github.andreygladkov.inklauncher.HomeActivity
import io.github.andreygladkov.inklauncher.R
import io.github.andreygladkov.inklauncher.apps.LauncherApp
import io.github.andreygladkov.inklauncher.home.AppTile
import io.github.andreygladkov.inklauncher.home.CustomIcon
import io.github.andreygladkov.inklauncher.home.FolderTile
import io.github.andreygladkov.inklauncher.home.LabeledTile
import io.github.andreygladkov.inklauncher.home.Placed
import io.github.andreygladkov.inklauncher.home.ScreenTimeTile
import io.github.andreygladkov.inklauncher.home.Spot
import io.github.andreygladkov.inklauncher.home.WidgetTile
import io.github.andreygladkov.inklauncher.home.renamed
import io.github.andreygladkov.inklauncher.home.takeGraphemes
import io.github.andreygladkov.inklauncher.home.withLabelHidden
import io.github.andreygladkov.inklauncher.icons.IconPack
import io.github.andreygladkov.inklauncher.screentime.AppCategory
import io.github.andreygladkov.inklauncher.screentime.ScreenTimeText

class TileMenus(private val activity: HomeActivity) {
    fun tile(placed: Placed) = when (val tile = placed.tile) {
        is AppTile -> homeApp(tile)
        is FolderTile -> folder(tile)
        is WidgetTile -> widget(tile)
        is ScreenTimeTile -> screenTime(tile)
    }

    fun emptyCell(spot: Spot) = sheet(null) {
        action(R.string.add_app) { activity.showAllApps(target = spot) }
        action(R.string.add_widget) { activity.showWidgetPicker(spot) }
        action(R.string.add_folder) {
            activity.dismissSheet()
            activity.update { it.place(FolderTile(it.nextId(), string(R.string.folder_default_name)), spot) }
        }
        action(R.string.new_page) {
            activity.dismissSheet()
            activity.home.addPage()
        }
        action(R.string.launcher_settings) {
            activity.dismissSheet()
            activity.openSettings()
        }
    }

    fun listedApp(app: LauncherApp) = sheet(app.label) {
        action(R.string.add_to_home) {
            activity.dismissSheet()
            activity.addToHome(app.key)
        }
        categoryAction(app.key.packageName, app.label)
        action(R.string.app_info) { activity.catalog.openDetails(app.key.packageName) }
        action(R.string.uninstall) { activity.catalog.uninstall(app.key.packageName) }
    }

    fun folderApp(app: AppTile) = sheet(activity.labelOf(app)) {
        labelActions(app)
        action(R.string.take_out_of_folder) {
            activity.dismissSheet()
            activity.update { it.takeOut(app.id) }
        }
        action(R.string.remove_from_folder) {
            activity.dismissSheet()
            activity.update { it.remove(app.id) }
        }
        categoryAction(app.app.packageName, activity.labelOf(app))
        action(R.string.app_info) { activity.catalog.openDetails(app.app.packageName) }
    }

    private fun homeApp(app: AppTile) = sheet(activity.labelOf(app)) {
        labelActions(app)
        moveAction(app.id)
        action(R.string.into_folder) { intoFolder(app) }
        action(R.string.remove_from_home) {
            activity.dismissSheet()
            activity.update { it.remove(app.id) }
        }
        categoryAction(app.app.packageName, activity.labelOf(app))
        action(R.string.app_info) { activity.catalog.openDetails(app.app.packageName) }
    }

    private fun folder(folder: FolderTile) = sheet(folder.name) {
        labelActions(folder)
        moveAction(folder.id)
        action(R.string.delete_folder) {}.confirmBeforeAction(R.string.delete_folder_confirm) {
            activity.dismissSheet()
            activity.update { it.remove(folder.id) }
        }
    }

    private fun widget(widget: WidgetTile) = sheet(activity.widgets.info(widget.widgetId)?.loadLabel(activity.packageManager) ?: string(R.string.widget)) {
        action(R.string.widget_size) { size(widget.id) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            action(if (widget.monochrome) R.string.widget_original_colors else R.string.widget_monochrome) {
                activity.dismissSheet()
                activity.update { layout -> layout.changed<WidgetTile>(widget.id) { it.copy(monochrome = !widget.monochrome) } }
            }
        }
        moveAction(widget.id)
        action(R.string.remove_from_home) {}.confirmBeforeAction(R.string.remove_widget_confirm) {
            activity.dismissSheet()
            activity.update { it.remove(widget.id) }
            activity.widgets.remove(widget.widgetId)
        }
    }

    private fun screenTime(tile: ScreenTimeTile) = sheet(string(R.string.screen_time)) {
        action(R.string.widget_size) { size(tile.id) }
        moveAction(tile.id)
        action(R.string.remove_from_home) {}.confirmBeforeAction(R.string.remove_widget_confirm) {
            activity.dismissSheet()
            activity.update { it.remove(tile.id) }
        }
    }

    fun appCategory(packageName: String, label: String) = sheet(label) {
        val current = activity.screenTime.category(packageName)
        AppCategory.entries.forEach { category ->
            action(ScreenTimeText.category(activity, category)) {
                activity.dismissSheet()
                activity.setCategory(packageName, category)
            }.isChecked = category == current
        }
    }

    private fun ActionSheet.categoryAction(packageName: String, label: String) {
        val current = ScreenTimeText.category(activity, activity.screenTime.category(packageName))
        action(activity.getString(R.string.screen_time_category, current)) { appCategory(packageName, label) }
    }

    private fun ActionSheet.labelActions(tile: LabeledTile) {
        action(R.string.rename) { rename(tile) }
        action(R.string.change_icon) { icon(tile) }
        action(if (tile.labelHidden) R.string.show_label else R.string.hide_label) {
            activity.dismissSheet()
            activity.update { layout -> layout.changed<LabeledTile>(tile.id) { it.withLabelHidden(!it.labelHidden) } }
        }
    }

    private fun ActionSheet.moveAction(id: Long) = action(R.string.move) {
        activity.dismissSheet()
        activity.home.startMove(id)
    }

    private fun rename(tile: LabeledTile) = textSheet(R.string.rename, activity.labelOf(tile)) { text ->
        activity.update { layout -> layout.changed<LabeledTile>(tile.id) { it.renamed(text) } }
    }.apply {
        if (tile is AppTile && tile.label != null) {
            action(R.string.original_name) {
                activity.dismissSheet()
                activity.update { layout -> layout.changed<LabeledTile>(tile.id) { it.renamed(null) } }
            }
        }
    }

    private fun icon(tile: LabeledTile) = sheet(string(R.string.change_icon)) {
        val packs = IconPack.installed(activity)
        action(R.string.icon_from_pack) {
            when (packs.size) {
                0 -> sheet(string(R.string.icon_from_pack)) {
                    content(InkTextView(activity).apply { setText(R.string.no_icon_packs) })
                    action(R.string.find_icon_packs) { IconPack.search(activity) }
                }
                1 -> activity.showPackIcons(packs.single(), tile.id)
                else -> sheet(string(R.string.icon_from_pack)) {
                    packs.forEach { pack -> action(pack.label) { activity.showPackIcons(pack, tile.id) } }
                }
            }
        }
        action(R.string.icon_picture) { activity.pickPicture(tile.id) }
        action(R.string.icon_text) {
            val current = (tile.icon as? CustomIcon.Text)?.text ?: activity.labelOf(tile).takeGraphemes(1).uppercase()
            textSheet(R.string.icon_text, current) { text -> activity.setIcon(tile.id, CustomIcon.Text(text.takeGraphemes(MAX_ICON_TEXT))) }
        }
        if (tile.icon != null) action(R.string.icon_reset) { activity.setIcon(tile.id, null) }
    }

    private fun intoFolder(app: AppTile) = sheet(string(R.string.into_folder)) {
        activity.layout.folders().forEach { folder ->
            action(folder.name) {
                activity.dismissSheet()
                activity.update { it.drop(app.id, folder.id, folder.name) }
            }
        }
        action(R.string.new_folder) {
            activity.dismissSheet()
            activity.update { it.intoNewFolder(app.id, string(R.string.folder_default_name)) }
        }
    }

    private fun size(id: Long) = sheet(string(R.string.widget_size_title)) {
        val columns = sizeRow(R.string.widget_width)
        val rows = sizeRow(R.string.widget_height)
        fun show() {
            val spot = activity.layout.find(id)?.spot ?: return
            fun resize(width: Int, height: Int): (() -> Unit)? {
                if (activity.layout.resized(id, width, height) == null) return null
                return {
                    activity.update { it.resized(id, width, height) }
                    show()
                }
            }
            columns.bind(spot.width, resize(spot.width - 1, spot.height), resize(spot.width + 1, spot.height))
            rows.bind(spot.height, resize(spot.width, spot.height - 1), resize(spot.width, spot.height + 1))
        }
        show()
    }

    private fun ActionSheet.sizeRow(@StringRes title: Int): SizeRow = SizeRow(title).also { content(it) }

    private inner class SizeRow(@StringRes title: Int) : LinearLayout(activity) {
        private val value = InkTextView(activity).apply { gravity = Gravity.CENTER }
        private val less = InkButton(activity).apply {
            text = "−"
            minWidth = minHeight
        }
        private val more = InkButton(activity).apply {
            text = "+"
            minWidth = minHeight
        }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(InkTextView(activity).apply { setText(title) }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(less, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(value, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = less.minHeight / 3
                marginEnd = less.minHeight / 3
            })
            addView(more, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }

        fun bind(current: Int, shrink: (() -> Unit)?, grow: (() -> Unit)?) {
            value.text = current.toString()
            listOf(less to shrink, more to grow).forEach { (button, resize) ->
                button.visibility = if (resize != null) VISIBLE else INVISIBLE
                button.setOnClickListener { resize?.invoke() }
            }
        }
    }

    private fun textSheet(@StringRes title: Int, current: String, onDone: (String) -> Unit): ActionSheet {
        val field = InkEditText(activity).apply {
            isBordered = true
            setText(current)
            setSelection(current.length)
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        fun done() {
            val text = field.text.toString().trim()
            activity.dismissSheet()
            if (text.isNotEmpty()) onDone(text)
        }
        field.setOnEditorActionListener { _, _, _ ->
            done()
            true
        }
        return sheet(string(title)) {
            content(field)
            action(R.string.done) { done() }
        }.also { activity.showKeyboard(field) }
    }

    private fun sheet(title: CharSequence?, build: ActionSheet.() -> Unit): ActionSheet =
        ActionSheet(activity, title?.toString()) { activity.dismissSheet() }.apply(build).also(activity::showSheet)

    private fun ActionSheet.action(@StringRes label: Int, onClick: () -> Unit) = action(string(label), onClick)

    private fun string(@StringRes id: Int) = activity.getString(id)

    private companion object {
        const val MAX_ICON_TEXT = 4
    }
}
