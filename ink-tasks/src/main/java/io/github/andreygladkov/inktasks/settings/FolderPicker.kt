package io.github.andreygladkov.inktasks.settings

import android.app.Activity
import android.content.Intent
import io.github.andreygladkov.inktasks.store.Storage

object FolderPicker {
    private const val REQUEST = 0x7a5c

    @Suppress("DEPRECATION")
    fun open(activity: Activity) {
        activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), REQUEST)
    }

    /** Null when the result is not the picker's; otherwise whether a folder was chosen and can be written. */
    fun chosen(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?): Boolean? {
        if (requestCode != REQUEST) return null
        val tree = data?.data?.takeIf { resultCode == Activity.RESULT_OK } ?: return false
        return Storage(activity).choose(tree)
    }
}
