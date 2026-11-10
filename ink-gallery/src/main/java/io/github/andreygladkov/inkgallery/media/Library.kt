package io.github.andreygladkov.inkgallery.media

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import android.provider.MediaStore.MediaColumns
import android.provider.OpenableColumns

object Library {
    private val FILES: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val ITEM_PROJECTION = arrayOf(
        MediaColumns._ID, MediaColumns.DISPLAY_NAME, MediaColumns.BUCKET_ID, MediaColumns.BUCKET_DISPLAY_NAME,
        MediaColumns.RELATIVE_PATH, MediaColumns.DATE_TAKEN, MediaColumns.DATE_ADDED, MediaColumns.WIDTH, MediaColumns.HEIGHT,
        MediaColumns.ORIENTATION, MediaColumns.SIZE, MediaColumns.DURATION,
    )

    /** Only the files table has the media type; an image's or a video's own table refuses a column it lacks. */
    private val FILES_PROJECTION = ITEM_PROJECTION + FileColumns.MEDIA_TYPE

    private const val PHOTOS_AND_VIDEOS =
        "${FileColumns.MEDIA_TYPE} IN (${FileColumns.MEDIA_TYPE_IMAGE}, ${FileColumns.MEDIA_TYPE_VIDEO})"

    private val newestFirst = compareByDescending<Item> { it.takenAt ?: 0L }.thenByDescending { ContentUris.parseId(it.uri) }

    /** The library's photos and videos, newest first; with [albumId], only that album's. */
    fun items(resolver: ContentResolver, albumId: String? = null): List<Item> {
        val selection = if (albumId == null) PHOTOS_AND_VIDEOS else "$PHOTOS_AND_VIDEOS AND ${MediaColumns.BUCKET_ID} = ?"
        val args = albumId?.let { arrayOf(it) }
        val items = resolver.query(FILES, FILES_PROJECTION, selection, args, null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.item(cursor.int(FileColumns.MEDIA_TYPE) == FileColumns.MEDIA_TYPE_VIDEO)) }
        }.orEmpty()
        return items.sortedWith(newestFirst)
    }

    /**
     * Albums in the order of their newest item, from [items] already sorted newest first. An item outside any
     * folder the library knows has no album to open, so it is only among all of them.
     */
    fun albums(items: List<Item>): List<Album> =
        items.filter { it.albumId != null }.groupBy { it.albumId!! }.map { (id, inAlbum) -> Album(id, inAlbum.first().albumName, inAlbum.size) }

    /**
     * What another app asked to open: found in the library when it is one of its items. It keeps the address it came
     * with, since the right to read it was granted for that address alone.
     */
    fun item(resolver: ContentResolver, uri: Uri): Item {
        val isVideo = isVideo(resolver, uri)
        val inLibrary = if (uri.authority == MediaStore.AUTHORITY) runCatching {
            resolver.query(uri, ITEM_PROJECTION, null, null, null)?.use { if (it.moveToFirst()) it.item(isVideo).copy(uri = uri) else null }
        }.getOrNull() else null
        return inLibrary ?: openable(resolver, uri, isVideo)
    }

    private fun isVideo(resolver: ContentResolver, uri: Uri) =
        runCatching { resolver.getType(uri) }.getOrNull()?.startsWith("video/") ?: uri.pathSegments.contains("video")

    private fun openable(resolver: ContentResolver, uri: Uri, isVideo: Boolean): Item {
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val (name, size) = runCatching {
            resolver.query(uri, columns, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) null
                else cursor.string(OpenableColumns.DISPLAY_NAME) to cursor.long(OpenableColumns.SIZE)
            }
        }.getOrNull() ?: (null to null)
        return Item(uri, name ?: uri.lastPathSegment.orEmpty(), isVideo = isVideo, sizeBytes = size)
    }

    private fun Cursor.item(isVideo: Boolean): Item {
        val id = long(MediaColumns._ID)!!
        val turned = int(MediaColumns.ORIENTATION) == 90 || int(MediaColumns.ORIENTATION) == 270
        val width = int(MediaColumns.WIDTH) ?: 0
        val height = int(MediaColumns.HEIGHT) ?: 0
        val table = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return Item(
            uri = ContentUris.withAppendedId(table, id),
            name = string(MediaColumns.DISPLAY_NAME).orEmpty(),
            isVideo = isVideo,
            isInLibrary = true,
            albumId = string(MediaColumns.BUCKET_ID),
            albumName = string(MediaColumns.BUCKET_DISPLAY_NAME),
            folder = string(MediaColumns.RELATIVE_PATH)?.trimEnd('/'),
            // Screenshots and saved pictures often have no capture time; the time they were added stands in for it.
            takenAt = long(MediaColumns.DATE_TAKEN)?.takeIf { it > 0 } ?: long(MediaColumns.DATE_ADDED)?.let { it * 1000 },
            width = if (turned) height else width,
            height = if (turned) width else height,
            sizeBytes = long(MediaColumns.SIZE),
            durationMs = if (isVideo) long(MediaColumns.DURATION) else null,
        )
    }

    private fun Cursor.index(column: String) = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }

    private fun Cursor.string(column: String) = index(column)?.let(::getString)

    private fun Cursor.long(column: String) = index(column)?.let(::getLong)

    private fun Cursor.int(column: String) = index(column)?.let(::getInt)
}
