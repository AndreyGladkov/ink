package io.github.andreygladkov.inkmessages.thread

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkButtonRow
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.inkmessages.R
import io.github.andreygladkov.inkmessages.data.Attachment
import io.github.andreygladkov.inkmessages.data.Loader
import io.github.andreygladkov.inkmessages.mms.Attachments
import kotlin.math.roundToInt

/** Shows an MMS picture as it is, in grey, unlike the 1-bit thumbnail in the thread. */
class ImageViewerActivity : Activity() {
    private val attachment by lazy {
        Attachment(intent.getLongExtra(EXTRA_PART_ID, -1), intent.getStringExtra(EXTRA_TYPE).orEmpty(), intent.getStringExtra(EXTRA_NAME))
    }
    private lateinit var image: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val style = InkStyle.of(this)
        image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.attachment_image)
            setBackgroundColor(style.background)
        }
        val save = InkButton(this).apply { setText(R.string.save) }
        save.setOnClickListener {
            save.isEnabled = false
            Attachments.saveImage(this, attachment) { saved -> save.setText(if (saved) R.string.saved else R.string.save_failed) }
        }
        val share = InkButton(this).apply {
            setText(R.string.share)
            setOnClickListener { Attachments.share(this@ImageViewerActivity, attachment) }
        }
        val actions = InkButtonRow(this, dp(ACTION_GAP_DP), 1f).apply {
            setPadding(dp(ACTION_GAP_DP), dp(ACTION_GAP_DP), dp(ACTION_GAP_DP), dp(ACTION_GAP_DP))
            addButton(save)
            addButton(share)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header())
            addView(line(style))
            addView(image, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(line(style))
            addView(actions)
        }
        InkWindow.setContent(this, root)
        load()
    }

    private fun load() {
        val resolver = contentResolver
        val metrics = resources.displayMetrics
        Loader.executor.execute {
            val bitmap = runCatching { decode(resolver, metrics.widthPixels, metrics.heightPixels) }.getOrNull() ?: return@execute
            runOnUiThread { if (!isDestroyed) image.setImageBitmap(bitmap) }
        }
    }

    private fun decode(resolver: ContentResolver, maxWidth: Int, maxHeight: Int): Bitmap? {
        val bounds = MmsImageView.bounds(resolver, attachment) ?: return null
        var sample = 1
        while (bounds.width / (sample * 2) >= maxWidth || bounds.height / (sample * 2) >= maxHeight) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(attachment.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun header() = InkHeader.of(this, getString(R.string.viewer_title), ::finish)

    private fun line(style: InkStyle) = View(this).apply {
        setBackgroundColor(style.foreground)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(InkStyle.LINE_DP).coerceAtLeast(1))
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_PART_ID = "part_id"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_NAME = "name"
        private const val ACTION_GAP_DP = 8f

        fun intent(context: Context, attachment: Attachment): Intent = Intent(context, ImageViewerActivity::class.java)
            .putExtra(EXTRA_PART_ID, attachment.partId)
            .putExtra(EXTRA_TYPE, attachment.contentType)
            .putExtra(EXTRA_NAME, attachment.name)
    }
}
