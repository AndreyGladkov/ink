package io.github.andreygladkov.inkgallery

import android.app.PendingIntent
import android.content.ClipData
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media
import android.provider.OpenableColumns
import androidx.core.graphics.createBitmap
import io.github.andreygladkov.inkgallery.media.Thumbnails
import org.robolectric.Robolectric
import java.io.File

/**
 * The system media library as far as the app sees it: photo and video rows, read together from its files table or
 * apart from their own, their files, and delete requests. A video's file is a picture too, which is enough for its
 * thumbnail; its frames and length come from a ShadowMediaMetadataRetriever.
 */
class FakeMedia : ContentProvider() {
    data class Row(
        val id: Long,
        val name: String,
        val albumId: String?,
        val albumName: String?,
        val takenAt: Long?,
        val addedAt: Long = 0,
        val width: Int = 30,
        val height: Int = 20,
        val orientation: Int = 0,
        val folder: String = "DCIM/$albumName/",
        val size: Long = 1234,
        val durationMs: Long? = null,
    ) {
        val isVideo get() = durationMs != null
    }

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val single = uri.lastPathSegment?.toLongOrNull()
        val album = if (selection?.contains(Media.BUCKET_ID) == true) selectionArgs?.last() else null
        val kind: (Row) -> Boolean = when {
            uri.pathSegments.contains("video") -> { row -> row.isVideo }
            uri.pathSegments.contains("images") -> { row -> !row.isVideo }
            else -> { _ -> true }
        }
        val columns = projection ?: arrayOf(Media._ID)
        require(!(single != null && MediaStore.Files.FileColumns.MEDIA_TYPE in columns && !uri.pathSegments.contains("file"))) {
            "Invalid column ${MediaStore.Files.FileColumns.MEDIA_TYPE}"
        }
        return MatrixCursor(columns).apply {
            rows.filter { kind(it) && (single == null || it.id == single) && (album == null || it.albumId == album) }.forEach { row ->
                addRow(columns.map { value(row, it) })
            }
        }
    }

    private fun value(row: Row, column: String): Any? = when (column) {
        Media._ID -> row.id
        Media.DISPLAY_NAME -> row.name
        Media.BUCKET_ID -> row.albumId
        Media.BUCKET_DISPLAY_NAME -> row.albumName
        Media.RELATIVE_PATH -> row.folder
        Media.DATE_TAKEN -> row.takenAt
        Media.DATE_ADDED -> row.addedAt
        Media.WIDTH -> row.width
        Media.HEIGHT -> row.height
        Media.ORIENTATION -> row.orientation
        Media.SIZE -> row.size
        MediaStore.MediaColumns.DURATION -> row.durationMs
        MediaStore.Files.FileColumns.MEDIA_TYPE ->
            if (row.isVideo) MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO else MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
        else -> null
    }

    override fun getType(uri: Uri) = if (uri.pathSegments.contains("video")) "video/mp4" else "image/png"

    override fun openTypedAssetFile(uri: Uri, mimeTypeFilter: String, opts: Bundle?) = openAssetFile(uri, "r")

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(files.getValue(ContentUris.parseId(uri)), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val clips = extras?.keySet()?.mapNotNull { extras.getParcelable(it, ClipData::class.java) }.orEmpty()
        deleteRequests += clips.flatMap { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } }
        val confirm = PendingIntent.getActivity(context, 0, Intent("confirm"), PendingIntent.FLAG_IMMUTABLE)
        return Bundle().apply { putParcelable("result", confirm) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        val rows = mutableListOf<Row>()
        val files = HashMap<Long, File>()
        val deleteRequests = mutableListOf<Uri>()

        fun install() {
            Thumbnails.clear()
            rows.clear()
            files.clear()
            deleteRequests.clear()
            Robolectric.setupContentProvider(FakeMedia::class.java, MediaStore.AUTHORITY)
        }

        fun uri(id: Long): Uri = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, id)

        fun videoUri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

        /** Adds a photo with a real picture file: a horizontal grey ramp, unless [picture] is given. */
        fun add(context: Context, row: Row, picture: Bitmap = ramp(row.width, row.height)) {
            rows += row
            files[row.id] = File(context.cacheDir, "photo-${row.id}.png").apply {
                outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }

        fun ramp(width: Int, height: Int) = createBitmap(width, height).apply {
            for (x in 0 until width) for (y in 0 until height) {
                val grey = x * 255 / (width - 1).coerceAtLeast(1)
                setPixel(x, y, Color.rgb(grey, grey, grey))
            }
        }
    }
}

/** A picture another app shares: a provider that knows only a name and a size. */
class FakeShared : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(listOf("shared.png", 4321L)) }

    override fun getType(uri: Uri) = type

    override fun openTypedAssetFile(uri: Uri, mimeTypeFilter: String, opts: Bundle?) = openAssetFile(uri, "r")

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(file!!, ParcelFileDescriptor.MODE_READ_ONLY)

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "test.shared"
        val uri: Uri = Uri.parse("content://$AUTHORITY/picture")
        var file: File? = null
        var type = "image/png"

        fun install(context: Context) {
            type = "image/png"
            file = File(context.cacheDir, "shared.png").apply {
                outputStream().use { FakeMedia.ramp(40, 20).compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            Robolectric.setupContentProvider(FakeShared::class.java, AUTHORITY)
        }
    }
}
