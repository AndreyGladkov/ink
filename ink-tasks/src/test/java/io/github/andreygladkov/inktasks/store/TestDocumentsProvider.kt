package io.github.andreygladkov.inktasks.store

import android.Manifest
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import org.robolectric.Robolectric
import java.io.File
import java.io.FileNotFoundException

/** A documents provider over a plain directory, standing in for the phone's storage provider behind the folder picker. */
class TestDocumentsProvider : DocumentsProvider() {
    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID))

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        cursor(projection).apply { add(this, documentId) }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val dir = file(parentDocumentId)
        if (!dir.isDirectory) throw FileNotFoundException(parentDocumentId)
        return cursor(projection).apply { dir.listFiles().orEmpty().forEach { add(this, "$parentDocumentId/${it.name}") } }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val created = File(file(parentDocumentId), displayName)
        if (mimeType == Document.MIME_TYPE_DIR) created.mkdir() else created.createNewFile()
        return "$parentDocumentId/$displayName"
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val renamed = File(file(documentId).parentFile, displayName)
        file(documentId).renameTo(renamed)
        return documentId.substringBeforeLast('/') + "/" + displayName
    }

    override fun deleteDocument(documentId: String) {
        file(documentId).deleteRecursively()
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String) = documentId.startsWith("$parentDocumentId/")

    private fun cursor(projection: Array<out String>?) =
        MatrixCursor(projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE))

    private fun add(cursor: MatrixCursor, id: String) {
        val file = file(id)
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, id)
            add(Document.COLUMN_DISPLAY_NAME, file.name)
            add(Document.COLUMN_MIME_TYPE, if (file.isDirectory) Document.MIME_TYPE_DIR else "text/markdown")
        }
    }

    private fun file(id: String) = File(base, id)

    companion object {
        const val AUTHORITY = "io.github.andreygladkov.inktasks.test.documents"
        lateinit var base: File

        /** Registers the provider over [dir] and returns the tree a picker would hand back for the folder [name] in it. */
        fun install(dir: File, name: String): Uri {
            base = dir
            File(dir, name).mkdirs()
            Robolectric.buildContentProvider(TestDocumentsProvider::class.java).create(ProviderInfo().apply {
                authority = AUTHORITY
                exported = true
                grantUriPermissions = true
                readPermission = Manifest.permission.MANAGE_DOCUMENTS
                writePermission = Manifest.permission.MANAGE_DOCUMENTS
            })
            return DocumentsContract.buildTreeDocumentUri(AUTHORITY, name)
        }
    }
}
