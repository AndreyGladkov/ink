package io.github.andreygladkov.inklauncher

object LauncherPrefs {
    const val ICON_PACK = "icon_pack"
    const val LABELS = "labels_shown"
    const val ICON_FRAME = "icon_frame"
    const val DRIFTING_APPS = "drifting_apps"
    const val ON_PURPOSE_APPS = "on_purpose_apps"
    const val UNCOUNTED_APPS = "uncounted_apps"
    const val NOTIFICATION_BADGE = "notification_badge"

    fun packInvertKey(packageName: String) = "icon_pack_invert_$packageName"
}
