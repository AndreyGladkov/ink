package io.github.andreygladkov.inkgallery.viewer

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkPagedActivity
import io.github.andreygladkov.ink.ui.InkPagedItems
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkgallery.R
import io.github.andreygladkov.inkgallery.media.Item
import io.github.andreygladkov.inkgallery.media.formatDuration
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class InfoActivity : InkPagedActivity(), ModeScreen {
    private val photo by lazy { Item.fromBundle(requireNotNull(intent.getBundleExtra(EXTRA_PHOTO))) }

    override fun headerTitle(): String = getString(R.string.info)

    override fun refreshMode() = clearIfWanted(this)

    override fun InkPagedItems.build() {
        facts().forEach { (label, value) -> item { fact(label, value) } }
    }

    private fun facts(): List<Pair<String, String>> = listOfNotNull(
        photo.takenAt?.let { getString(R.string.info_taken) to DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(it)) },
        if (photo.width > 0 && photo.height > 0) getString(R.string.info_resolution) to getString(R.string.info_pixels, photo.width, photo.height) else null,
        photo.durationMs?.let { getString(R.string.info_duration) to formatDuration(it) },
        photo.sizeBytes?.let { getString(R.string.info_size) to Formatter.formatShortFileSize(this, it) },
        getString(R.string.info_name) to photo.name,
        (photo.folder ?: photo.albumName)?.let { getString(R.string.info_folder) to it },
    )

    private fun fact(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val gap = (FACT_GAP_DP * resources.displayMetrics.density).roundToInt()
        setPadding(0, gap, 0, gap)
        addView(InkTextView(this@InfoActivity).apply {
            text = label
            textScale = InkStyle.SECONDARY_SCALE
        })
        addView(InkTextView(this@InfoActivity).apply { text = value })
    }

    companion object {
        private const val EXTRA_PHOTO = "photo"
        private const val FACT_GAP_DP = 6f

        fun intent(context: Context, photo: Item): Intent = Intent(context, InfoActivity::class.java).putExtra(EXTRA_PHOTO, photo.toBundle())
    }
}
