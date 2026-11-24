package io.github.andreygladkov.inktasks.store

interface Folder {
    val isAvailable: Boolean get() = true

    fun names(): List<String>

    fun read(name: String): String?

    fun write(name: String, text: String)

    fun create(name: String): Boolean

    fun rename(from: String, to: String): Boolean

    fun delete(name: String)

    companion object {
        const val EXTENSION = ".md"
    }
}
