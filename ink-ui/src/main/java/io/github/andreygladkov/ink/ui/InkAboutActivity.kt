package io.github.andreygladkov.ink.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import io.github.andreygladkov.ink.R
import io.github.andreygladkov.ink.eink.InkStyle
import kotlin.math.roundToInt

/** An app's version, its own notes and the licenses of what it is built from; a license opens as text in the same screen. */
abstract class InkAboutActivity : InkPagedActivity() {
    protected abstract fun notes(): List<String>

    /** Components only this app uses, listed before the ones every ink app has. */
    protected open fun InkPagedItems.addOwnComponents() = Unit

    override fun headerTitle(): String = getString(R.string.about)

    override fun InkPagedItems.build() {
        val license = intent.getStringExtra(EXTRA_LICENSE)
        if (license != null) preformatted(licenseText(license)) else showAbout()
    }

    private fun InkPagedItems.showAbout() {
        val activity = this@InkAboutActivity
        item {
            InkTextView(activity).apply {
                text = applicationInfo.loadLabel(packageManager)
                textScale = InkStyle.TITLE_SCALE
                isBold = true
            }
        }
        item { InkTextView(activity).apply { text = getString(R.string.about_version, packageManager.getPackageInfo(packageName, 0).versionName) } }
        val gap = (NOTE_GAP_DP * resources.displayMetrics.density).roundToInt()
        notes().forEach { note ->
            item {
                InkTextView(activity).apply {
                    text = note
                    setPadding(0, gap, 0, 0)
                }
            }
        }
        license(getString(R.string.about_gpl), GPL)
        link(getString(R.string.about_source), SOURCE_URL)
        section(getString(R.string.about_components))
        addOwnComponents()
        license(getString(R.string.about_noto), OFL)
        license(getString(R.string.about_material), APACHE)
    }

    protected fun InkPagedItems.license(label: String, asset: String) =
        component(label) { startActivity(Intent(this@InkAboutActivity, this@InkAboutActivity::class.java).putExtra(EXTRA_LICENSE, asset)) }

    protected fun InkPagedItems.link(label: String, url: String) = component(label) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun InkPagedItems.component(label: String, onClick: () -> Unit) = item {
        InkSettings.rowButton(this@InkAboutActivity, label, onClick).apply { textScale = InkStyle.SECONDARY_SCALE }
    }

    private fun licenseText(asset: String) =
        runCatching { assets.open("licenses/$asset").bufferedReader().use { it.readText() } }.getOrDefault("")

    companion object {
        const val APACHE = "Apache-2.0.txt"
        private const val GPL = "GPL-3.0.txt"
        private const val OFL = "OFL-1.1.txt"
        private const val NOTE_GAP_DP = 8f
        private const val EXTRA_LICENSE = "license"
        private const val SOURCE_URL = "https://github.com/AndreyGladkov/ink"
    }
}
