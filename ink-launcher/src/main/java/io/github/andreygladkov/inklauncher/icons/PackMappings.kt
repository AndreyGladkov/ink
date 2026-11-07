package io.github.andreygladkov.inklauncher.icons

import io.github.andreygladkov.inklauncher.home.AppKey
import java.io.File

/**
 * The drawable a pack gives each installed app, one file per pack version. An empty drawable records that the
 * pack has none for the app, so the app does not send the pack to be read again.
 */
class PackMappings(private val directory: File) {
    fun read(pack: String, version: Long): Map<AppKey, String>? {
        val file = file(pack, version).takeIf { it.exists() } ?: return null
        return runCatching {
            file.readLines().mapNotNull { line ->
                val tab = line.indexOf(SEPARATOR)
                if (tab < 0) null else AppKey.unflatten(line.substring(0, tab))?.let { it to line.substring(tab + 1) }
            }.toMap()
        }.getOrNull()
    }

    fun write(pack: String, version: Long, mapping: Map<AppKey, String>) {
        directory.mkdirs()
        directory.listFiles { file -> file.name.startsWith("$pack@") }?.forEach { it.delete() }
        file(pack, version).writeText(mapping.entries.joinToString("\n") { (key, drawable) -> "${key.flatten()}$SEPARATOR$drawable" })
    }

    private fun file(pack: String, version: Long) = File(directory, "$pack@$version.tsv")

    private companion object {
        const val SEPARATOR = '\t'
    }
}
