package io.github.andreygladkov.inktasks.store

import android.util.AtomicFile
import java.io.File

class LocalFolder(private val dir: File) : Folder {
    override fun names(): List<String> =
        dir.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(Folder.EXTENSION) }.map { it.name.removeSuffix(Folder.EXTENSION) }

    override fun read(name: String): String? = file(name).takeIf { it.baseFile.exists() }?.readFully()?.toString(Charsets.UTF_8)

    override fun write(name: String, text: String) {
        dir.mkdirs()
        val file = file(name)
        val stream = file.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }

    override fun create(name: String): Boolean {
        if (name in names()) return false
        write(name, "")
        return true
    }

    /** File.renameTo replaces an existing target on Unix instead of failing. */
    override fun rename(from: String, to: String): Boolean {
        if (to in names()) return false
        return path(from).renameTo(path(to))
    }

    override fun delete(name: String) {
        file(name).delete()
    }

    private fun path(name: String) = File(dir, name + Folder.EXTENSION)

    private fun file(name: String) = AtomicFile(path(name))
}
