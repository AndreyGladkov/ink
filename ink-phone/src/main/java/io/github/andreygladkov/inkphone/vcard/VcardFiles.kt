package io.github.andreygladkov.inkphone.vcard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

object VcardFiles {
    const val MIME = "text/x-vcard"
    val OPEN_MIME_TYPES = arrayOf(MIME, "text/vcard", "text/directory", "application/octet-stream")
    private val UNSAFE = Regex("""[\\/:*?"<>|\s]+""")

    fun fileName(cards: List<CardData>): String {
        val single = cards.singleOrNull()?.displayName?.replace(UNSAFE, "_")?.trim('_')
        return (single?.ifBlank { null } ?: "contacts-${LocalDate.now()}") + ".vcf"
    }

    fun write(activity: Activity, uri: Uri, cards: List<CardData>) {
        requireNotNull(activity.contentResolver.openOutputStream(uri, "wt")).use { VcardMapper.write(cards, it) }
    }

    fun shareableFile(activity: Activity, cards: List<CardData>): Uri {
        val dir = File(activity.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName(cards))
        file.outputStream().use { VcardMapper.write(cards, it) }
        return FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
    }

    fun share(activity: Activity, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(send, null))
    }

    fun createDocumentIntent(cards: List<CardData>): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(MIME)
        .putExtra(Intent.EXTRA_TITLE, fileName(cards))
}
