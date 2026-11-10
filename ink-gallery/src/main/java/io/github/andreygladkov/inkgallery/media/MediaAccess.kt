package io.github.andreygladkov.inkgallery.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Since Android 14 a person may let the app see only the photos and videos they pick, which is [PARTIAL]; so is
 * access to only one of the two.
 */
enum class MediaAccess {
    FULL, PARTIAL, NONE;

    companion object {
        val permissions: List<String> = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        private val full = permissions.filter { it != Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }

        fun of(context: Context): MediaAccess {
            val granted = permissions.filter { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
            return when {
                granted.containsAll(full) -> FULL
                granted.isNotEmpty() -> PARTIAL
                else -> NONE
            }
        }
    }
}
