package io.github.andreygladkov.inkgallery

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.BackToStart
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkTabBar
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.PermissionGate
import io.github.andreygladkov.inkgallery.grid.AlbumActivity
import io.github.andreygladkov.inkgallery.grid.AlbumList
import io.github.andreygladkov.inkgallery.grid.GridSelection
import io.github.andreygladkov.inkgallery.grid.PhotoGrid
import io.github.andreygladkov.inkgallery.media.MediaAccess
import io.github.andreygladkov.inkgallery.media.Item
import io.github.andreygladkov.inkgallery.media.Library
import io.github.andreygladkov.inkgallery.settings.SettingsActivity
import io.github.andreygladkov.inkgallery.viewer.ViewerActivity
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val gate by lazy { PermissionGate(this, MediaAccess.permissions, REQUEST_PHOTOS) }
    private lateinit var grid: PhotoGrid
    private lateinit var albums: AlbumList
    private lateinit var tabBar: InkTabBar<Tab>
    private lateinit var header: View
    private lateinit var empty: InkTextView
    private lateinit var noAccess: LinearLayout
    private lateinit var grantAccess: InkButton
    private lateinit var partialAccess: LinearLayout
    private lateinit var allowMore: InkButton
    private val backToStart = BackToStart(this) { tabBar.select(Tab.PHOTOS) }
    private lateinit var selection: GridSelection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        grid = PhotoGrid(this) { startActivity(ViewerActivity.open(this, it, null)) }
        albums = AlbumList(this) { startActivity(AlbumActivity.intent(this, it)) }
        header = InkHeader.of(this, InkHeader.title(this, ""), null, InkHeader.settings(this) { startActivity(SettingsActivity.intent(this)) })
        empty = InkTextView(this).apply {
            setText(R.string.no_photos)
            gravity = Gravity.CENTER
        }
        tabBar = InkTabBar(this, Tab.entries, inkPrefs().getBoolean(InkTabBar.PREF_LABELS, true), ::showTab)
        selection = GridSelection(this, grid, header, tabBar, ::refresh)
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(selection.top, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(partialAccess(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(FrameLayout(this@MainActivity).apply {
                addView(grid.view)
                addView(albums.view)
                addView(empty, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
                addView(noAccess(), FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
            }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(selection.bottom, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })
        tabBar.select(Tab.PHOTOS)
    }

    override fun onStart() {
        super.onStart()
        refresh()
    }

    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!selection.onBackPressed() && !backToStart.onBackPressed()) @Suppress("DEPRECATION") super.onBackPressed()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        selection.onActivityResult(requestCode, resultCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PHOTOS) refresh()
    }

    private fun showTab(tab: Tab) {
        InkHeader.setTitle(header, getString(tab.title))
        backToStart.isAway = tab != Tab.PHOTOS
        grid.view.visibility = if (tab == Tab.PHOTOS) View.VISIBLE else View.INVISIBLE
        albums.view.visibility = if (tab == Tab.ALBUMS) View.VISIBLE else View.INVISIBLE
    }

    private fun refresh() {
        val access = MediaAccess.of(this)
        noAccess.visibility = if (access == MediaAccess.NONE) View.VISIBLE else View.GONE
        partialAccess.visibility = if (access == MediaAccess.PARTIAL) View.VISIBLE else View.GONE
        if (access == MediaAccess.PARTIAL) gate.bind(allowMore)
        if (access == MediaAccess.NONE) {
            gate.bind(grantAccess)
            show(emptyList())
            empty.visibility = View.GONE
            return
        }
        val resolver = contentResolver
        Background.queries.execute {
            val photos = runCatching { Library.items(resolver) }.getOrDefault(emptyList())
            runOnUiThread { if (!isDestroyed) show(photos) }
        }
    }

    private fun show(photos: List<Item>) {
        grid.setPhotos(photos)
        albums.setAlbums(Library.albums(photos))
        empty.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun noAccess() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val padding = dp(PADDING_DP)
        setPadding(padding, padding, padding, padding)
        addView(InkTextView(this@MainActivity).apply {
            setText(R.string.access_needed)
            gravity = Gravity.CENTER
        })
        grantAccess = InkButton(this@MainActivity)
        addView(grantAccess, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = padding })
        noAccess = this
    }

    /**
     * Asking again shows the system's choice with "Allow all", which also covers access to photos given before videos
     * were asked for; once asking is refused for good, the button leads to the app's system settings instead.
     */
    private fun partialAccess() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padding = dp(PADDING_DP)
        setPadding(padding, 0, 0, 0)
        addView(InkTextView(this@MainActivity).apply {
            setText(R.string.access_partial)
            textScale = InkStyle.SECONDARY_SCALE
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        allowMore = InkButton(this@MainActivity).apply {
            textScale = InkStyle.SECONDARY_SCALE
            isBordered = false
        }
        addView(allowMore)
        visibility = View.GONE
        partialAccess = this
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val REQUEST_PHOTOS = 1
        const val PADDING_DP = 16f
    }
}
