package io.github.andreygladkov.inklauncher

object LauncherPrefs {
    const val ICON_PACK = "icon_pack"
    const val LABELS = "labels_shown"
    const val ICON_FRAME = "icon_frame"
    const val DRIFTING_APPS = "drifting_apps"

    fun packInvertKey(packageName: String) = "icon_pack_invert_$packageName"
}
