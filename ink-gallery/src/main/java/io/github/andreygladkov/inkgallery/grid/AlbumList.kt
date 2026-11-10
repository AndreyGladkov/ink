package io.github.andreygladkov.inkgallery.grid

import android.content.Context
import android.view.Gravity
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.twoLines
import io.github.andreygladkov.inkgallery.R
import io.github.andreygladkov.inkgallery.media.Album

/** Albums as rows of their name and how many photos they hold, without covers. */
class AlbumList(private val context: Context, private val onOpen: (Album) -> Unit) {
    val view = InkPagedList(context)
    private var albums: List<Album> = emptyList()
    private var isFilled = false
    private val rows = object : InkPagedList.Adapter {
        override val count: Int get() = albums.size
        override fun itemId(position: Int) = position.toLong()
        override fun createView(position: Int) = row(albums[position])
    }

    fun setAlbums(albums: List<Album>) {
        if (isFilled && albums == this.albums) return
        isFilled = true
        val first = albums.indexOfFirst { it.id == this.albums.getOrNull(view.firstPosition)?.id }.coerceAtLeast(0)
        this.albums = albums
        view.replaceAdapter(rows, first)
    }

    private fun row(album: Album) = InkButton(context).apply {
        text = twoLines(album.name ?: context.getString(R.string.album_unnamed), context.resources.getQuantityString(R.plurals.photo_count, album.count, album.count))
        isBordered = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { onOpen(album) }
    }
}
