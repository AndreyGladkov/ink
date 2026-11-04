package io.github.andreygladkov.inkphone.vcard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.RawContacts
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.addSection
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.canReadContacts
import io.github.andreygladkov.inkphone.contacts.AccountLabels
import io.github.andreygladkov.inkphone.contacts.AccountRef
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class ExportActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val density get() = resources.displayMetrics.density
    private lateinit var scopes: LinearLayout
    private lateinit var status: InkTextView
    private lateinit var pickButton: InkButton
    private var scope: ContactCards.Scope = ContactCards.Scope.All
    private var selected: Set<Long> = emptySet()
    private var pendingCards: List<CardData> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = dp(PADDING_DP)
        scopes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = InkTextView(this).apply {
            textScale = InkStyle.SECONDARY_SCALE
            gravity = Gravity.CENTER
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, padding)
            addView(InkTextView(context).apply {
                setText(R.string.export_contacts)
                textScale = InkStyle.TITLE_SCALE
                isBold = true
            })
            addSection(getString(R.string.export_what))
            addView(scopes)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkButton(this@ExportActivity).apply {
                icon = R.drawable.ic_arrow_back
                contentDescription = getString(R.string.back)
                isBordered = false
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(ScrollView(this@ExportActivity).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(body)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, 0, padding, padding)
                addView(status, rowParams())
                addView(action(R.string.export_to_file, R.drawable.ic_download) { export(toFile = true) }, rowParams())
                addView(action(R.string.share, R.drawable.ic_share) { export(toFile = false) }, rowParams())
            })
        }
        InkWindow.setContent(this, root)
        if (canReadContacts()) load() else requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_READ)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_READ) return
        if (canReadContacts()) load() else finish()
    }

    private fun load() {
        loader.execute {
            val accounts = LinkedHashSet<AccountRef>()
            runCatching {
                contentResolver.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME), "${RawContacts.DELETED}=0", null, null)?.use {
                    while (it.moveToNext()) accounts += AccountRef(it.getString(0), it.getString(1))
                }
            }
            main.post { if (!isDestroyed) showScopes(accounts.toList()) }
        }
    }

    private fun showScopes(accounts: List<AccountRef>) {
        val labels = AccountLabels(this)
        val options = listOf<Pair<ContactCards.Scope, String>>(ContactCards.Scope.All to getString(R.string.export_all)) +
            accounts.map { ContactCards.Scope.Account(it) to labels.label(it) }
        scopes.removeAllViews()
        options.forEach { (option, label) ->
            scopes.addView(scopeButton(label) { choose(option, this) }.apply { isSelected = option == scope }, rowParams())
        }
        pickButton = scopeButton(getString(R.string.export_selected)) {
            @Suppress("DEPRECATION")
            startActivityForResult(ContactPickActivity.intent(this@ExportActivity, selected), REQUEST_PICK)
        }
        scopes.addView(pickButton, rowParams())
    }

    private fun scopeButton(label: String, onClick: InkButton.() -> Unit) = InkButton(this).apply {
        text = label
        textScale = InkStyle.SECONDARY_SCALE
        maxLines = 1
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { onClick() }
    }

    private fun choose(option: ContactCards.Scope, button: View) {
        scope = option
        (0 until scopes.childCount).forEach { scopes.getChildAt(it).isSelected = scopes.getChildAt(it) === button }
        status.text = if (option is ContactCards.Scope.Contacts) getString(R.string.export_selected_count, option.ids.size) else ""
    }

    private fun export(toFile: Boolean) {
        val chosen = scope
        if (chosen is ContactCards.Scope.Contacts && chosen.ids.isEmpty()) return
        val cards = ContactCards(contentResolver)
        loader.execute {
            val read = runCatching { cards.read(chosen) }.getOrDefault(emptyList())
            val shared = if (!toFile && read.isNotEmpty()) runCatching { VcardFiles.shareableFile(this, read) }.getOrNull() else null
            main.post {
                if (isDestroyed || read.isEmpty()) return@post
                if (toFile) {
                    pendingCards = read
                    @Suppress("DEPRECATION")
                    startActivityForResult(VcardFiles.createDocumentIntent(read), REQUEST_CREATE)
                } else if (shared != null) {
                    VcardFiles.share(this, shared)
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQUEST_PICK -> {
                selected = data.getLongArrayExtra(ContactPickActivity.EXTRA_SELECTED)?.toSet().orEmpty()
                choose(ContactCards.Scope.Contacts(selected), pickButton)
            }
            REQUEST_CREATE -> {
                val uri = data.data ?: return
                val cards = pendingCards
                loader.execute {
                    val written = runCatching { VcardFiles.write(this, uri, cards) }.isSuccess
                    main.post { status.text = if (written) getString(R.string.export_done, cards.size) else getString(R.string.export_failed) }
                }
            }
        }
    }

    private fun action(text: Int, icon: Int, onClick: () -> Unit) = InkButton(this).apply {
        setText(text)
        this.icon = icon
        iconPosition = InkButton.IconPosition.START
        iconSizeDp = ICON_DP
        setOnClickListener { onClick() }
    }

    private fun rowParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(GAP_DP)
    }

    private fun dp(value: Float) = (value * density).roundToInt()

    private companion object {
        const val REQUEST_CREATE = 71
        const val REQUEST_PICK = 72
        const val PADDING_DP = 16f
        const val GAP_DP = 8f
        const val REQUEST_READ = 91
        const val ICON_DP = 24f
        val loader = Executors.newSingleThreadExecutor()
    }
}
