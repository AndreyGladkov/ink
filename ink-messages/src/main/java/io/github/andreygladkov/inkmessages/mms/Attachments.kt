package io.github.andreygladkov.inkmessages.mms

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import io.github.andreygladkov.inkmessages.data.Attachment
import io.github.andreygladkov.inkmessages.data.Loader

/**
 * Hands MMS parts to other apps. Parts live in the telephony provider, which other apps cannot read, so each
 * is copied into [MmsFiles] first and shared through a one-off read grant.
 */
object Attachments {
    private val main = Handler(Looper.getMainLooper())

    fun open(activity: Activity, attachment: Attachment, onNoApp: () -> Unit) = withCopy(activity, attachment) { uri ->
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, attachment.contentType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            activity.startActivity(view)
        } catch (_: ActivityNotFoundException) {
            onNoApp()
        }
    }

    fun share(activity: Activity, attachment: Attachment) = withCopy(activity, attachment) { uri ->
        val send = Intent(Intent.ACTION_SEND).setType(attachment.contentType).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(send, null))
    }

    /** Saves a picture to the shared Pictures folder, which needs no permission on API 29 and later. */
    fun saveImage(context: Context, attachment: Attachment, done: (Boolean) -> Unit) {
        val app = context.applicationContext
        Loader.executor.execute {
            val saved = runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName(attachment))
                    put(MediaStore.Images.Media.MIME_TYPE, attachment.contentType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
                }
                val target = app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
                copy(app, attachment.uri, target)
                true
            }.getOrDefault(false)
            main.post { done(saved) }
        }
    }

    /** A name other apps can show and pick a handler by: the sender's name when given, with an extension for its type. */
    fun fileName(attachment: Attachment): String {
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(attachment.contentType)
        val given = attachment.name?.substringAfterLast('/')?.replace(UNSAFE, "_")?.trim('.', ' ', '_')?.takeIf(String::isNotEmpty)
        val base = given ?: "mms-${attachment.partId}"
        return if (extension == null || base.substringAfterLast('.', "").equals(extension, ignoreCase = true)) base else "$base.$extension"
    }

    private fun withCopy(activity: Activity, attachment: Attachment, use: (Uri) -> Unit) {
        val app = activity.applicationContext
        Loader.executor.execute {
            val uri = runCatching {
                val file = MmsFiles.file(app, "${attachment.partId}-${fileName(attachment)}")
                copy(app, attachment.uri, Uri.fromFile(file))
                MmsFiles.uri(app, file)
            }.getOrNull() ?: return@execute
            main.post { if (!activity.isDestroyed) use(uri) }
        }
    }

    private fun copy(context: Context, from: Uri, to: Uri) {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(from) ?: error("no data for $from")
        input.use { source -> (resolver.openOutputStream(to) ?: error("cannot write $to")).use { source.copyTo(it) } }
    }

    private const val FOLDER = "Ink Messages"
    private val UNSAFE = Regex("""[^\p{L}\p{N}._\- ]""")
}
