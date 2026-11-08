package io.github.andreygladkov.inklauncher.home

import android.icu.text.BreakIterator

data class AppKey(val packageName: String, val className: String) {
    fun flatten() = "$packageName/$className"

    companion object {
        fun unflatten(value: String): AppKey? {
            val slash = value.indexOf('/')
            if (slash <= 0 || slash == value.lastIndex) return null
            val pkg = value.substring(0, slash)
            val cls = value.substring(slash + 1)
            return AppKey(pkg, if (cls.startsWith(".")) pkg + cls else cls)
        }
    }
}

sealed interface CustomIcon {
    data class Picture(val file: String) : CustomIcon
    data class Text(val text: String) : CustomIcon
    data class FromPack(val pack: String, val drawable: String) : CustomIcon
}

sealed interface Tile {
    val id: Long
}

sealed interface LabeledTile : Tile {
    val label: String?
    val icon: CustomIcon?
    val labelHidden: Boolean
}

data class AppTile(
    override val id: Long,
    val app: AppKey,
    override val label: String? = null,
    override val icon: CustomIcon? = null,
    override val labelHidden: Boolean = false,
) : LabeledTile

data class FolderTile(
    override val id: Long,
    val name: String,
    val apps: List<AppTile> = emptyList(),
    override val icon: CustomIcon? = null,
    override val labelHidden: Boolean = false,
) : LabeledTile {
    override val label: String get() = name
}

data class WidgetTile(override val id: Long, val widgetId: Int, val provider: AppKey) : Tile

data class ScreenTimeTile(override val id: Long) : Tile {
    companion object {
        const val COLUMNS = 2
        const val ROWS = 1
    }
}

fun LabeledTile.renamed(label: String?): LabeledTile = when (this) {
    is AppTile -> copy(label = label?.takeIf { it.isNotBlank() })
    is FolderTile -> copy(name = label?.takeIf { it.isNotBlank() } ?: name)
}

fun LabeledTile.withIcon(icon: CustomIcon?): LabeledTile = when (this) {
    is AppTile -> copy(icon = icon)
    is FolderTile -> copy(icon = icon)
}

fun LabeledTile.withLabelHidden(hidden: Boolean): LabeledTile = when (this) {
    is AppTile -> copy(labelHidden = hidden)
    is FolderTile -> copy(labelHidden = hidden)
}

/** Cuts by what reads as one character, so an emoji built of several code points is never split. */
fun String.takeGraphemes(count: Int): String {
    val boundaries = BreakIterator.getCharacterInstance().apply { setText(this@takeGraphemes) }
    var end = 0
    repeat(count) {
        val next = boundaries.following(end)
        if (next == BreakIterator.DONE) return this
        end = next
    }
    return substring(0, end)
}
