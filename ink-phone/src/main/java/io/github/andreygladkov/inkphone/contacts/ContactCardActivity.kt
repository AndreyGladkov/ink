package io.github.andreygladkov.inkphone.contacts

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.text.SpannableStringBuilder
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.DottedDrawable
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkButtonRow
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.addSection
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.call.CallLauncher
import io.github.andreygladkov.inkphone.call.ContactLookup
import io.github.andreygladkov.inkphone.call.messageAndCallButtons
import io.github.andreygladkov.inkphone.calllog.CallLabels
import io.github.andreygladkov.inkphone.calllog.CallLogEntry
import io.github.andreygladkov.inkphone.calllog.CallLogGrouping
import io.github.andreygladkov.inkphone.calllog.CallLogRepository
import io.github.andreygladkov.inkphone.calllog.CallType
import io.github.andreygladkov.inkphone.canReadContacts
import io.github.andreygladkov.inkphone.favorites.SpeedDial
import io.github.andreygladkov.inkphone.hasPermission
import io.github.andreygladkov.inkphone.vcard.ContactCards
import io.github.andreygladkov.inkphone.vcard.VcardFiles
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class ContactCardActivity : Activity() {
    private val density get() = resources.displayMetrics.density
    private val accountLabels by lazy { AccountLabels(this) }
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(PADDING_DP)
            setPadding(padding, 0, padding, padding)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkButton(this@ContactCardActivity).apply {
                icon = R.drawable.ic_arrow_back
                contentDescription = getString(R.string.back)
                isBordered = false
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(ScrollView(this@ContactCardActivity).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(body)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        InkWindow.setContent(this, root)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val number = intent.getStringExtra(EXTRA_NUMBER)?.takeIf { it.isNotBlank() }
        val contactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1)
        val external = ContactIntents.contactUri(intent)
        if (number == null && (contactId >= 0 || external != null) && !canReadContacts()) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_READ)
            return
        }
        load(contactId, number, external)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT && resultCode == ContactEditActivity.RESULT_DELETED) finish()
    }

    private fun load(contactId: Long, number: String?, external: Uri?) {
        val repository = ContactsRepository(contentResolver)
        val calls = CallLogRepository(contentResolver)
        val canReadContacts = canReadContacts()
        val canReadLog = hasPermission(Manifest.permission.READ_CALL_LOG)
        val main = Handler(Looper.getMainLooper())
        loader.execute {
            val loaded = runCatching {
                val details = if (canReadContacts) {
                    val id = when {
                        number != null -> ContactLookup.find(this, number)?.contactId ?: -1
                        external != null -> ContactIntents.contactId(contentResolver, external) ?: -1
                        else -> contactId
                    }
                    repository.loadDetails(id)
                } else {
                    null
                } ?: number?.let {
                    ContactDetails(id = -1, name = it, phones = listOf(PhoneEntry(-1, it, NUMBER_ONLY, null, false)), emails = emptyList(), accounts = emptyList())
                }
                val history = if (details != null && canReadLog) {
                    calls.load().filter { entry -> details.phones.any { CallLogGrouping.sameNumber(it.number, entry.number) } }.take(HISTORY_SIZE)
                } else {
                    emptyList()
                }
                details to history
            }.getOrDefault(null to emptyList())
            main.post { if (!isDestroyed) show(loaded.first, loaded.second) }
        }
    }

    private fun show(details: ContactDetails?, history: List<CallLogEntry>) {
        body.removeAllViews()
        if (details == null) {
            body.addView(text(getString(R.string.contact_not_found)))
            return
        }
        body.addView(text(details.name).apply {
            textScale = InkStyle.TITLE_SCALE
            isBold = true
        })
        body.addView(actions(details))
        if (details.phones.isNotEmpty()) {
            body.addSection(getString(R.string.card_phones))
            details.phones.forEachIndexed { index, phone ->
                if (index > 0) body.addView(dottedLine())
                body.addView(phoneRow(phone, canMakeDefault = details.phones.size > 1 && !phone.isDefault && phone.dataId >= 0))
            }
        }
        if (details.emails.isNotEmpty()) {
            body.addSection(getString(R.string.card_emails))
            details.emails.forEach { body.addView(text(it)) }
        }
        if (history.isNotEmpty()) {
            val labels = CallLabels(this)
            body.addSection(getString(R.string.card_history))
            history.forEachIndexed { index, entry ->
                if (index > 0) body.addView(dottedLine())
                body.addView(text(SpannableStringBuilder(labels.summary(entry)).append(duration(entry))).apply { isBold = entry.type == CallType.MISSED })
            }
        }
        if (details.id >= 0 && details.phones.isNotEmpty()) addSpeedDial(details)
        if (details.accounts.isNotEmpty()) {
            body.addSection(getString(R.string.card_accounts))
            details.accounts.forEach { body.addView(text(accountLabels.label(it))) }
        }
    }

    private fun actions(details: ContactDetails) = InkButtonRow(this, dp(ROW_GAP_DP), InkStyle.SECONDARY_SCALE).apply {
        setPadding(0, dp(ROW_GAP_DP), 0, 0)
        val buttons = if (details.id >= 0) {
            listOf(
                action(if (details.starred) R.string.favorite_remove else R.string.favorite_add,
                    if (details.starred) R.drawable.ic_tab_favorites else R.drawable.ic_star_outline) {
                    write { it.setStarred(details.id, !details.starred) }
                },
                action(R.string.edit, R.drawable.ic_edit) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(ContactEditActivity.intentForEdit(this@ContactCardActivity, details.id), REQUEST_EDIT)
                },
                action(R.string.share, R.drawable.ic_share) { share(details.id) },
            )
        } else {
            listOf(action(R.string.add_to_contacts, R.drawable.ic_add) {
                startActivity(ContactEditActivity.intentForNew(this@ContactCardActivity, details.phones.firstOrNull()?.number))
                finish()
            })
        }
        buttons.forEach { addButton(it) }
    }

    private fun share(contactId: Long) {
        val cards = ContactCards(contentResolver)
        loader.execute {
            val uri = runCatching { cards.read(ContactCards.Scope.Contacts(setOf(contactId))) }.getOrDefault(emptyList())
                .takeIf { it.isNotEmpty() }?.let { runCatching { VcardFiles.shareableFile(this, it) }.getOrNull() }
            Handler(Looper.getMainLooper()).post { if (uri != null && !isDestroyed) VcardFiles.share(this, uri) }
        }
    }

    private fun addSpeedDial(details: ContactDetails) {
        val speedDial = SpeedDial(inkPrefs())
        val numbers = details.phones.map { it.number }
        val number = (details.phones.firstOrNull { it.isDefault } ?: details.phones.first()).number
        val options = listOf<Char?>(null) + SpeedDial.KEYS
        body.addView(InkChoiceRow(this, getString(R.string.speed_dial),
            options.map { it to (it?.toString() ?: getString(R.string.speed_dial_none)) }, speedDial.keyFor(numbers),
            decorate = { isBordered = false },
        ) { key -> speedDial.assign(key, number, numbers) })
    }

    private fun action(text: Int, icon: Int, onClick: () -> Unit) = InkButton(this).apply {
        setText(text)
        this.icon = icon
        iconSizeDp = ACTION_ICON_DP
        isBordered = false
        setOnClickListener { onClick() }
    }

    private fun write(change: (ContactWriter) -> Unit) {
        if (!hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), REQUEST_WRITE)
            return
        }
        val writer = ContactWriter(contentResolver)
        loader.execute {
            runCatching { change(writer) }
            Handler(Looper.getMainLooper()).post { if (!isDestroyed) reload() }
        }
    }

    private fun phoneRow(phone: PhoneEntry, canMakeDefault: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(ROW_GAP_DP), 0, dp(ROW_GAP_DP))
        val type = if (phone.type == NUMBER_ONLY) null else Phone.getTypeLabel(resources, phone.type, phone.label).toString()
        val details = listOfNotNull(type, getString(R.string.default_number).takeIf { phone.isDefault }).joinToString(" · ")
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(phone.number).apply { isBold = true })
            if (details.isNotEmpty()) addView(text(details).apply { textScale = InkStyle.SECONDARY_SCALE })
            if (canMakeDefault) addView(InkButton(context).apply {
                setText(R.string.make_default_number)
                textScale = InkStyle.SECONDARY_SCALE
                isBordered = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                minHeight = 0
                setPadding(0, dp(ROW_GAP_DP), 0, dp(ROW_GAP_DP))
                paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                setOnClickListener { write { it.setDefaultPhone(phone.dataId) } }
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(messageAndCallButtons(this@ContactCardActivity, phone.number))
    }

    private fun dottedLine() = View(this).apply {
        background = DottedDrawable(context)
        minimumHeight = background.intrinsicHeight
    }

    private fun text(value: CharSequence) = InkTextView(this).apply {
        text = value
        setPadding(0, dp(ROW_GAP_DP), 0, dp(ROW_GAP_DP))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        CallLauncher.onPermissionsResult(this, requestCode, grantResults)
        if (requestCode == REQUEST_READ) {
            if (canReadContacts()) reload() else finish()
        }
    }

    private fun duration(entry: CallLogEntry): String {
        val seconds = entry.durationSeconds
        return when {
            seconds <= 0 -> ""
            seconds < 60 -> " · ${getString(R.string.duration_seconds, seconds.toInt())}"
            else -> " · ${getString(R.string.duration_minutes, (seconds / 60).toInt())}"
        }
    }

    private fun dp(value: Float) = (value * density).roundToInt()

    companion object {
        private const val EXTRA_CONTACT_ID = "contact_id"
        private const val EXTRA_NUMBER = "number"
        private const val NUMBER_ONLY = -1
        private const val REQUEST_EDIT = 61
        private const val REQUEST_WRITE = 62
        private const val REQUEST_READ = 63
        private const val HISTORY_SIZE = 10
        private const val ACTION_ICON_DP = 20f
        private const val PADDING_DP = 16f
        private const val ROW_GAP_DP = 6f
        private val loader = Executors.newSingleThreadExecutor()

        fun intent(context: Context, contactId: Long): Intent =
            Intent(context, ContactCardActivity::class.java).putExtra(EXTRA_CONTACT_ID, contactId)

        fun intentForNumber(context: Context, number: String): Intent =
            Intent(context, ContactCardActivity::class.java).putExtra(EXTRA_NUMBER, number)
    }
}
