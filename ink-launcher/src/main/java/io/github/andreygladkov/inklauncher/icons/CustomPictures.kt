package io.github.andreygladkov.inklauncher.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/** Pictures picked for icons are copied in, shrunk, so the icon outlives the original file and its permission. */
object CustomPictures {
    private const val DIRECTORY = "custom-icons"
    private const val MAX_SIDE_PX = 256

    fun import(context: Context, uri: Uri): String? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val side = maxOf(info.size.width, info.size.height)
            if (side > MAX_SIDE_PX) {
                val ratio = MAX_SIDE_PX.toFloat() / side
                decoder.setTargetSize((info.size.width * ratio).roundToInt().coerceAtLeast(1), (info.size.height * ratio).roundToInt().coerceAtLeast(1))
            }
        }
        val name = "${UUID.randomUUID()}.png"
        File(directory(context), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        name
    }.getOrNull()

    fun load(context: Context, name: String): Bitmap? = BitmapFactory.decodeFile(File(directory(context), name).path)

    fun keepOnly(context: Context, names: Set<String>) {
        directory(context).listFiles()?.filter { it.name !in names }?.forEach { it.delete() }
    }

    private fun directory(context: Context) = File(context.filesDir, DIRECTORY).apply { mkdirs() }
}
