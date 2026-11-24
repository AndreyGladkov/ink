package io.github.andreygladkov.inktasks.store

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.inktasks.R
import java.io.File

class Storage(private val context: Context) {
    private val prefs get() = context.inkPrefs()

    val phone: Folder get() = LocalFolder(File(context.filesDir, DIR))

    val chosen: DocumentFolder? get() = prefs.getString(PREF_FOLDER, null)?.let { DocumentFolder(context, it.toUri()) }

    val folder: Folder get() = chosen ?: phone

    /**
     * Lists on the phone move into the folder, renamed when the folder already has one of the name; a folder chosen
     * instead of another one is taken as it is. False, with nothing changed, when the folder cannot be written.
     */
    fun choose(tree: Uri): Boolean {
        context.contentResolver.takePersistableUriPermission(tree, ACCESS)
        val target = DocumentFolder(context, tree)
        val previous = chosen
        val moved = if (previous == null) {
            runCatching { copy(phone, target, context.getString(R.string.from_phone)) }.getOrElse {
                release(tree)
                return false
            }
        } else emptyList()
        moved.forEach(phone::delete)
        prefs.edit { putString(PREF_FOLDER, tree.toString()) }
        previous?.tree?.takeIf { it != tree }?.let(::release)
        return true
    }

    /** The folder's lists are copied back to the phone so that leaving the folder loses nothing from the screen. */
    fun keepOnPhone() {
        val previous = chosen ?: return
        if (previous.isAvailable) runCatching { copy(previous, phone, context.getString(R.string.from_folder)) }
        prefs.edit { remove(PREF_FOLDER) }
        release(previous.tree)
    }

    private fun copy(from: Folder, to: Folder, suffix: String): List<String> {
        val taken = to.names().toMutableList()
        return from.names().mapNotNull { name ->
            val text = from.read(name) ?: return@mapNotNull null
            val free = freeName(name, taken, suffix)
            to.write(free, text)
            taken += free
            name
        }
    }

    private fun release(tree: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(tree, ACCESS) }
    }

    companion object {
        internal const val DIR = "tasks"
        private const val PREF_FOLDER = "folder"
        private const val ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        fun freeName(name: String, taken: List<String>, suffix: String): String =
            generateSequence(1) { it + 1 }
                .map { number -> if (number == 1) name else if (number == 2) "$name$suffix" else "$name$suffix $number" }
                .first { candidate -> taken.none { it.equals(candidate, ignoreCase = true) } }
    }
}
