package io.github.andreygladkov.inkgallery.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import io.github.andreygladkov.ink.eink.A9Panel
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkButtonRow
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.confirmBeforeAction
import io.github.andreygladkov.inkgallery.GalleryPrefs
import io.github.andreygladkov.inkgallery.Background
import io.github.andreygladkov.inkgallery.R
import io.github.andreygladkov.inkgallery.media.Item
import io.github.andreygladkov.inkgallery.media.Library
import io.github.andreygladkov.inkgallery.video.VideoActivity
import io.github.andreygladkov.ink.R as InkR

/**
 * Photos one at a time, turned through in the order of the grid they were opened from. A video among them shows
 * its first frame with a play button, which plays it on a screen of its own. A picture another app opens here is
 * shown alone.
 */
class ViewerActivity : Activity(), ModeScreen {
    private lateinit var photoView: PhotoView
    private lateinit var controls: List<View>
    private lateinit var counter: InkTextView
    private lateinit var deleteButton: InkButton
    private lateinit var unreadable: InkTextView
    private lateinit var play: InkButton
    private var photos: List<Item> = emptyList()
    private var index = 0
    private var deleting: Uri? = null

    val shownPhoto: Item? get() = photos.getOrNull(index)

    override fun refreshMode() = clearIfWanted(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoView = PhotoView(this).apply {
            onFlip = ::flip
            onToggleControls = ::toggleControls
            onShown = { readable -> unreadable.visibility = if (readable) View.GONE else View.VISIBLE }
        }
        counter = InkHeader.title(this, "")
        unreadable = InkTextView(this).apply {
            setText(R.string.unreadable)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        play = InkButton(this).apply {
            icon = R.drawable.ic_play
            iconSizeDp = PLAY_ICON_DP
            contentDescription = getString(R.string.play)
            visibility = View.GONE
            setOnClickListener { shownPhoto?.let { startActivity(VideoActivity.intent(this@ViewerActivity, it)) } }
        }
        val top = Overlay.bar(this, InkHeader.of(this, counter, InkHeader.back(this, ::finish)))
        val bottom = Overlay.bar(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkHeader.line(this@ViewerActivity))
            addView(InkButtonRow(this@ViewerActivity, 0, InkStyle.SECONDARY_SCALE).apply {
                addButton(Overlay.action(this@ViewerActivity, R.drawable.ic_share, R.string.share) { share() })
                addButton(Overlay.action(this@ViewerActivity, R.drawable.ic_info, R.string.info) { shownPhoto?.let { startActivity(InfoActivity.intent(this@ViewerActivity, it)) } })
                deleteButton = Overlay.action(this@ViewerActivity, R.drawable.ic_delete, R.string.delete) {}
                addButton(deleteButton)
            })
        })
        controls = listOf(top, bottom)
        InkWindow.setContent(this, FrameLayout(this).apply {
            addView(photoView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(unreadable, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
            addView(play, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
            addView(top, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))
            addView(bottom, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        })
        deleting = savedInstanceState?.getString(STATE_DELETING)?.let(Uri::parse)
        load(savedInstanceState?.getString(STATE_PHOTO)?.let(Uri::parse))
    }

    override fun onStart() {
        super.onStart()
        val rotates = GalleryPrefs.rotatesLandscape(this)
        val dithers = PhotoDither.isOn(this)
        if (rotates == photoView.rotatesLandscape && dithers == photoView.dithers) return
        photoView.rotatesLandscape = rotates
        photoView.dithers = dithers
        if (photos.isNotEmpty()) showCurrent()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        shownPhoto?.let { outState.putString(STATE_PHOTO, it.uri.toString()) }
        deleting?.let { outState.putString(STATE_DELETING, it.toString()) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DELETE && resultCode == RESULT_OK) onDeleted()
    }

    private fun load(restored: Uri?) {
        val opened = intent.data.takeIf { intent.action == Intent.ACTION_VIEW }
        val start = restored ?: opened ?: IntentCompat.getParcelableExtra(intent, EXTRA_PHOTO, Uri::class.java)
        val album = intent.getStringExtra(EXTRA_ALBUM)
        val resolver = contentResolver
        Background.queries.execute {
            val found = runCatching {
                if (opened != null) listOf(Library.item(resolver, opened)) else Library.items(resolver, album)
            }.getOrDefault(emptyList())
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                photos = found
                index = found.indexOfFirst { it.uri == start }.coerceAtLeast(0)
                if (photos.isEmpty()) finish() else showCurrent()
            }
        }
    }

    private fun showCurrent() {
        val photo = shownPhoto ?: return
        photoView.show(photo, photos.getOrNull(index + 1))
        counter.text = if (photos.size > 1) getString(InkR.string.page_of, index + 1, photos.size) else photo.name
        play.isVisible = photo.isVideo
        deleteButton.visibility = if (photo.isInLibrary) View.VISIBLE else View.GONE
        deleteButton.setText(R.string.delete)
        deleteButton.confirmBeforeAction(R.string.delete_confirm, ::requestDelete)
    }

    private fun flip(step: Int) {
        val target = index + step
        if (target !in photos.indices) return
        index = target
        showCurrent()
        A9Panel.onPageFlipped(this)
    }

    private fun toggleControls() {
        Overlay.toggle(controls)
    }

    private fun share() {
        val photo = shownPhoto ?: return
        val send = Intent(Intent.ACTION_SEND)
            .setType(contentResolver.getType(photo.uri) ?: if (photo.isVideo) "video/*" else "image/*")
            .putExtra(Intent.EXTRA_STREAM, photo.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, null))
    }

    /** With the "manage media" right the system deletes at once; without it, it asks first in its own dialog. */
    private fun requestDelete() {
        val photo = shownPhoto ?: return
        deleting = photo.uri
        val request = MediaStore.createDeleteRequest(contentResolver, listOf(photo.uri))
        @Suppress("DEPRECATION") startIntentSenderForResult(request.intentSender, REQUEST_DELETE, null, 0, 0, 0)
    }

    /**
     * A screen recreated while the system asked about deleting gets the answer before its list is read again; the
     * list read then no longer has the deleted item, so there is nothing to remove yet.
     */
    private fun onDeleted() {
        val deleted = deleting ?: return
        deleting = null
        if (photos.isEmpty()) return
        photos = photos.filter { it.uri != deleted }
        if (photos.isEmpty()) return finish()
        index = index.coerceAtMost(photos.lastIndex)
        showCurrent()
    }

    companion object {
        private const val EXTRA_PHOTO = "photo"
        private const val EXTRA_ALBUM = "album"
        private const val STATE_PHOTO = "photo"
        private const val STATE_DELETING = "deleting"
        private const val REQUEST_DELETE = 1
        private const val PLAY_ICON_DP = 64f

        /** A video tapped in a grid plays at once; a photo opens among the others of that grid. */
        fun open(context: Context, item: Item, albumId: String?): Intent =
            if (item.isVideo) VideoActivity.intent(context, item) else intent(context, item, albumId)

        fun intent(context: Context, photo: Item, albumId: String?): Intent = Intent(context, ViewerActivity::class.java)
            .putExtra(EXTRA_PHOTO, photo.uri)
            .putExtra(EXTRA_ALBUM, albumId)
    }
}
