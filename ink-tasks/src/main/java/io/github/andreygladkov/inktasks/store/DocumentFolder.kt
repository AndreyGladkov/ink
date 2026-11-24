package io.github.andreygladkov.inktasks.store

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.FileNotFoundException

class DocumentFolder(private val context: Context, val tree: Uri) : Folder {
    private class Entry(val id: String, val name: String, val isDirectory: Boolean)

    private val resolver get() = context.contentResolver
    private val rootId get() = DocumentsContract.getTreeDocumentId(tree)
    private val root get() = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)

    val displayName: String?
        get() = runCatching {
            resolver.query(root, arrayOf(Document.COLUMN_DISPLAY_NAME), null as Bundle?, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()

    override val isAvailable: Boolean
        get() = resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission } &&
            runCatching { entries() }.isSuccess

    override fun names(): List<String> = files().map { it.name.removeSuffix(Folder.EXTENSION) }

    override fun read(name: String): String? = uri(name)?.let { uri ->
        resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    override fun write(name: String, text: String) {
        val uri = uri(name) ?: createFile(name)
        val stream = resolver.openOutputStream(uri, TRUNCATE) ?: throw FileNotFoundException(uri.toString())
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    override fun create(name: String): Boolean {
        if (uri(name) != null) return false
        createFile(name)
        return true
    }

    override fun rename(from: String, to: String): Boolean {
        val uri = uri(from) ?: return false
        if (uri(to) != null) return false
        return DocumentsContract.renameDocument(resolver, uri, to + Folder.EXTENSION) != null
    }

    override fun delete(name: String) {
        uri(name)?.let { DocumentsContract.deleteDocument(resolver, it) }
    }

    /**
     * A provider appends the extension of the MIME type it is given unless the name already maps to it; with the
     * generic type the name is kept as it is, whether or not the provider knows `.md`.
     */
    private fun createFile(name: String): Uri =
        DocumentsContract.createDocument(resolver, root, GENERIC_TYPE, name + Folder.EXTENSION) ?: throw FileNotFoundException(name)

    private fun uri(name: String): Uri? = files().firstOrNull { it.name == name + Folder.EXTENSION }?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it.id) }

    private fun files() = entries().filter { !it.isDirectory && it.name.endsWith(Folder.EXTENSION) }

    private fun entries(): List<Entry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val cursor = resolver.query(children, columns, null as Bundle?, null) ?: throw FileNotFoundException(tree.toString())
        return cursor.use {
            buildList {
                while (it.moveToNext()) add(Entry(it.getString(0), it.getString(1), it.getString(2) == Document.MIME_TYPE_DIR))
            }
        }
    }

    private companion object {
        /** "w" alone leaves the old tail of a longer file in place on some providers. */
        const val TRUNCATE = "wt"
        const val GENERIC_TYPE = "application/octet-stream"
    }
}
