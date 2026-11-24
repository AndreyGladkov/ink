package io.github.andreygladkov.inktasks.store

object ListName {
    /** Characters Obsidian refuses in a note's file name, plus the path separators every file system refuses. */
    const val FORBIDDEN = "*\"\\/<>:|?#^[]"

    fun strip(text: CharSequence): String = text.filterNot { it in FORBIDDEN }.toString()

    /** A dot in front would hide the file and keep Obsidian from showing it. */
    fun clean(text: CharSequence): String? = strip(text).trim().trimStart('.').trim().takeIf { it.isNotEmpty() }
}
