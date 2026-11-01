package io.github.andreygladkov.inkphone.vcard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.edit
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.contacts.AccountLabels
import io.github.andreygladkov.inkphone.contacts.AccountRef
import io.github.andreygladkov.inkphone.contacts.ContactWriter
import io.github.andreygladkov.inkphone.inkPrefs
import io.github.andreygladkov.inkphone.ui.InkButton
import io.github.andreygladkov.inkphone.ui.InkChoiceRow
import io.github.andreygladkov.inkphone.ui.InkPagedList
import io.github.andreygladkov.inkphone.ui.InkTextView
import io.github.andreygladkov.inkphone.ui.InkWindow
import io.github.andreygladkov.inkphone.ui.addSection
import io.github.andreygladkov.inkphone.ui.twoLines
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class ImportActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val density get() = resources.displayMetrics.density
    private lateinit var body: LinearLayout
    private var items: List<ImportItem> = emptyList()
    private var accounts: List<AccountRef> = emptyList()
    private var account: AccountRef = ContactWriter.LOCAL
    private var policy = DuplicatePolicy.SKIP
    private var importing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(PADDING_DP)
            setPadding(padding, 0, padding, padding)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkButton(this@ImportActivity).apply {
                icon = R.drawable.ic_arrow_back
                contentDescription = getString(R.string.back)
                isBordered = false
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        InkWindow.setContent(this, root)
        val uri = intent.data
        when {
            !hasPermissions() -> requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS)
            uri != null -> load(uri)
            savedInstanceState == null -> pickFile()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!hasPermissions()) return finish()
        intent.data?.let(::load) ?: pickFile()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_OPEN) return
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) finish() else load(uri)
    }

    private fun pickFile() {
        val open = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, VcardFiles.OPEN_MIME_TYPES)
        @Suppress("DEPRECATION")
        startActivityForResult(open, REQUEST_OPEN)
    }

    private fun load(uri: Uri) {
        val cards = ContactCards(contentResolver)
        val writer = ContactWriter(contentResolver)
        loader.execute {
            val parsed = runCatching { requireNotNull(contentResolver.openInputStream(uri)).use(VcardMapper::read) }.getOrNull()
            val matched = parsed?.let { ImportPlan.match(it, cards.existing()) }
            val writable = writer.writableAccounts()
            main.post {
                if (isDestroyed) return@post
                if (matched.isNullOrEmpty()) showMessage(getString(R.string.import_failed)) else show(matched, writable)
            }
        }
    }

    private fun show(matched: List<ImportItem>, writable: List<AccountRef>) {
        items = matched
        accounts = writable
        val remembered = inkPrefs().getString(ContactWriter.PREF_DEFAULT_ACCOUNT, null)?.let(AccountRef::decode)
        account = remembered?.takeIf { it in writable } ?: writable.first()
        body.removeAllViews()
        body.addView(InkTextView(this).apply {
            setText(R.string.import_title)
            textScale = TITLE_SCALE
            isBold = true
        })
        body.addView(InkTextView(this).apply {
            text = getString(R.string.import_found, matched.size, matched.count { it.match != null })
            textScale = SMALL_SCALE
        }, rowParams())
        body.addView(InkPagedList(this).apply {
            setAdapter(object : InkPagedList.Adapter {
                override val count = matched.size
                override fun itemId(position: Int) = position.toLong()
                override fun createView(position: Int): View = previewRow(matched[position])
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val labels = AccountLabels(this)
        body.addSection(getString(R.string.contact_account))
        writable.forEach { option ->
            body.addView(InkButton(this).apply {
                text = labels.label(option)
                textScale = SMALL_SCALE
                maxLines = 1
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                isSelected = option == account
                tag = ACCOUNT_TAG
                setOnClickListener {
                    account = option
                    (0 until body.childCount).map(body::getChildAt).filter { it.tag == ACCOUNT_TAG }.forEach { it.isSelected = it === this }
                }
            }, rowParams())
        }
        if (matched.any { it.match != null }) {
            body.addView(InkChoiceRow(this, getString(R.string.import_duplicates),
                listOf(DuplicatePolicy.SKIP to getString(R.string.duplicates_skip), DuplicatePolicy.MERGE to getString(R.string.duplicates_merge),
                    DuplicatePolicy.ADD to getString(R.string.duplicates_add)),
                policy,
            ) { policy = it })
        }
        body.addView(InkButton(this).apply {
            setText(R.string.import_start)
            icon = R.drawable.ic_upload
            iconPosition = InkButton.IconPosition.START
            iconSizeDp = ICON_DP
            setOnClickListener { start(this) }
        }, rowParams())
    }

    private fun previewRow(item: ImportItem): View = InkButton(this).apply {
        val details = listOfNotNull(
            item.card.phones.firstOrNull()?.value ?: item.card.emails.firstOrNull()?.value,
            getString(R.string.import_existing_mark).takeIf { item.match != null },
        ).joinToString(" · ")
        text = if (details.isEmpty()) item.card.displayName else twoLines(item.card.displayName, details)
        isBordered = false
        isClickable = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
    }

    private fun start(button: InkButton) {
        if (importing) return
        importing = true
        inkPrefs().edit { putString(ContactWriter.PREF_DEFAULT_ACCOUNT, account.encode()) }
        val target = account
        val chosenPolicy = policy
        val plan = items
        val writable = accounts
        val cards = ContactCards(contentResolver)
        val total = plan.size
        loader.execute {
            var added = 0
            var merged = 0
            val report = { main.post { button.text = getString(R.string.import_progress, added + merged, total) } }
            val fresh = plan.filter { it.match == null || chosenPolicy == DuplicatePolicy.ADD }
            val completed = runCatching {
                cards.insert(fresh.map { it.card }, target) { step ->
                    added += step
                    report()
                }
                if (chosenPolicy == DuplicatePolicy.MERGE) {
                    plan.filter { it.match != null }.forEach { item ->
                        cards.merge(item.card, item.match!!, writable, target)
                        merged++
                        report()
                    }
                }
            }.isSuccess
            val skipped = if (chosenPolicy == DuplicatePolicy.SKIP) plan.count { it.match != null } else 0
            main.post {
                if (isDestroyed) return@post
                showMessage(
                    if (completed) getString(R.string.import_done, added, merged, skipped)
                    else getString(R.string.import_interrupted, added, merged),
                )
            }
        }
    }

    private fun showMessage(text: String) {
        body.removeAllViews()
        body.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        body.addView(InkTextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
        })
        body.addView(InkButton(this).apply {
            setText(R.string.back)
            setOnClickListener { finish() }
        }, rowParams())
        body.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
    }

    private fun hasPermissions() = PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun rowParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(GAP_DP)
    }

    private fun dp(value: Float) = (value * density).roundToInt()

    private companion object {
        const val REQUEST_OPEN = 81
        const val REQUEST_PERMISSIONS = 82
        const val ACCOUNT_TAG = "account"
        const val PADDING_DP = 16f
        const val GAP_DP = 8f
        const val ICON_DP = 20f
        const val TITLE_SCALE = 1.2f
        const val SMALL_SCALE = 0.9f
        val PERMISSIONS = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        val loader = Executors.newSingleThreadExecutor()
    }
}
