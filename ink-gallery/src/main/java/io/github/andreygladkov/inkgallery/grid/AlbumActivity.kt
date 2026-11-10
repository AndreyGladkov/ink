package io.github.andreygladkov.inkgallery.grid

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.inkgallery.Background
import io.github.andreygladkov.inkgallery.R
import io.github.andreygladkov.inkgallery.media.Album
import io.github.andreygladkov.inkgallery.media.Library
import io.github.andreygladkov.inkgallery.viewer.ViewerActivity

class AlbumActivity : Activity() {
    private val albumId by lazy { requireNotNull(intent.getStringExtra(EXTRA_ALBUM)) }
    private lateinit var grid: PhotoGrid
    private lateinit var selection: GridSelection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        grid = PhotoGrid(this) { startActivity(ViewerActivity.open(this, it, albumId)) }
        val title = intent.getStringExtra(EXTRA_NAME) ?: getString(R.string.album_unnamed)
        val header = InkHeader.of(this, title, ::finish)
        selection = GridSelection(this, grid, header, null, ::reload)
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(selection.top)
            addView(FrameLayout(this@AlbumActivity).apply {
                addView(grid.view)
                addView(selection.bottom, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
            }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
    }

    override fun onStart() {
        super.onStart()
        reload()
    }

    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!selection.onBackPressed()) @Suppress("DEPRECATION") super.onBackPressed()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        selection.onActivityResult(requestCode, resultCode)
    }

    private fun reload() {
        val resolver = contentResolver
        Background.queries.execute {
            val photos = runCatching { Library.items(resolver, albumId) }.getOrDefault(emptyList())
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (photos.isEmpty()) finish() else grid.setPhotos(photos)
            }
        }
    }

    companion object {
        private const val EXTRA_ALBUM = "album"
        private const val EXTRA_NAME = "name"

        fun intent(context: Context, album: Album): Intent =
            Intent(context, AlbumActivity::class.java).putExtra(EXTRA_ALBUM, album.id).putExtra(EXTRA_NAME, album.name)
    }
}
