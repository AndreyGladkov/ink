package io.github.andreygladkov.inkgallery.media

import android.net.Uri
import android.os.Bundle
import androidx.core.os.BundleCompat

/**
 * A photo or a video to show. Ones from the library can be deleted; one another app opens here may come from
 * anywhere and is known only as far as its provider tells.
 */
data class Item(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean = false,
    val isInLibrary: Boolean = false,
    val albumId: String? = null,
    val albumName: String? = null,
    val folder: String? = null,
    val takenAt: Long? = null,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
) {
    fun toBundle() = Bundle().apply {
        putParcelable(KEY_URI, uri)
        putString(KEY_NAME, name)
        putBoolean(KEY_VIDEO, isVideo)
        putBoolean(KEY_IN_LIBRARY, isInLibrary)
        putString(KEY_ALBUM_ID, albumId)
        putString(KEY_ALBUM_NAME, albumName)
        putString(KEY_FOLDER, folder)
        takenAt?.let { putLong(KEY_TAKEN_AT, it) }
        putInt(KEY_WIDTH, width)
        putInt(KEY_HEIGHT, height)
        sizeBytes?.let { putLong(KEY_SIZE, it) }
        durationMs?.let { putLong(KEY_DURATION, it) }
    }

    companion object {
        private const val KEY_URI = "uri"
        private const val KEY_NAME = "name"
        private const val KEY_VIDEO = "video"
        private const val KEY_IN_LIBRARY = "in_library"
        private const val KEY_ALBUM_ID = "album_id"
        private const val KEY_ALBUM_NAME = "album_name"
        private const val KEY_FOLDER = "folder"
        private const val KEY_TAKEN_AT = "taken_at"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_SIZE = "size"
        private const val KEY_DURATION = "duration"

        fun fromBundle(bundle: Bundle) = Item(
            uri = requireNotNull(BundleCompat.getParcelable(bundle, KEY_URI, Uri::class.java)),
            name = bundle.getString(KEY_NAME).orEmpty(),
            isVideo = bundle.getBoolean(KEY_VIDEO),
            isInLibrary = bundle.getBoolean(KEY_IN_LIBRARY),
            albumId = bundle.getString(KEY_ALBUM_ID),
            albumName = bundle.getString(KEY_ALBUM_NAME),
            folder = bundle.getString(KEY_FOLDER),
            takenAt = bundle.getLong(KEY_TAKEN_AT).takeIf { bundle.containsKey(KEY_TAKEN_AT) },
            width = bundle.getInt(KEY_WIDTH),
            height = bundle.getInt(KEY_HEIGHT),
            sizeBytes = bundle.getLong(KEY_SIZE).takeIf { bundle.containsKey(KEY_SIZE) },
            durationMs = bundle.getLong(KEY_DURATION).takeIf { bundle.containsKey(KEY_DURATION) },
        )
    }
}

data class Album(val id: String, val name: String?, val count: Int)
