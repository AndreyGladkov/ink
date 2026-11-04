package io.github.andreygladkov.inkmessages.mms

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves files to the system MMS service and to apps opening attachments. Not exported: access comes only
 * from per-URI grants, which the MMS service receives through SmsManager and apps through the opening intent.
 */
class MmsFiles : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileOf(uri), ParcelFileDescriptor.parseMode(mode))

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val file = fileOf(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> file.name
                    OpenableColumns.SIZE -> file.length()
                    else -> null
                }
            })
        }
    }

    override fun getType(uri: Uri): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(uri.lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase())

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    private fun fileOf(uri: Uri): File {
        val name = uri.pathSegments.singleOrNull()?.takeIf { it.isNotEmpty() && "/" !in it && it != "." && it != ".." } ?: throw FileNotFoundException(uri.toString())
        return file(context!!, name)
    }

    companion object {
        private const val DIR = "mms"

        fun file(context: Context, name: String): File = File(File(context.cacheDir, DIR).apply { mkdirs() }, name)

        fun uri(context: Context, file: File): Uri = Uri.Builder().scheme("content").authority(authority(context)).appendPath(file.name).build()

        private fun authority(context: Context) = "${context.packageName}.mmsfiles"
    }
}
