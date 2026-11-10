package io.github.andreygladkov.inkgallery.viewer

import android.app.Activity
import android.content.Intent
import android.provider.MediaStore
import io.github.andreygladkov.inkgallery.media.Item

/** Sharing and deleting, the same from the grid, the viewer and the player. */
object Actions {
    const val REQUEST_DELETE = 41

    fun share(activity: Activity, items: List<Item>) {
        val first = items.firstOrNull() ?: return
        val send = if (items.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, first.uri)
                .setType(activity.contentResolver.getType(first.uri) ?: typeOf(items))
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
                .setType(typeOf(items))
        }
        activity.startActivity(Intent.createChooser(send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
    }

    /**
     * One system request for all of [items], answered in onActivityResult with [REQUEST_DELETE]. With the "manage
     * media" right the system deletes at once; without it, it asks first in its own dialog.
     */
    fun askToDelete(activity: Activity, items: List<Item>) {
        if (items.isEmpty()) return
        val request = MediaStore.createDeleteRequest(activity.contentResolver, items.map { it.uri })
        @Suppress("DEPRECATION") activity.startIntentSenderForResult(request.intentSender, REQUEST_DELETE, null, 0, 0, 0)
    }

    private fun typeOf(items: List<Item>) = when {
        items.all { it.isVideo } -> "video/*"
        items.none { it.isVideo } -> "image/*"
        else -> "*/*"
    }
}
