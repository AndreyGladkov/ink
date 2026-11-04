package io.github.andreygladkov.inkphone.contacts

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.edit
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.addSection
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.hasPermission
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class ContactEditActivity : Activity() {
    private class PhoneRow(val dataId: Long?, val field: InkEditText, var type: Int)
    private class EmailRow(val dataId: Long?, val field: InkEditText)

    private val main = Handler(Looper.getMainLooper())
    private val density get() = resources.displayMetrics.density
    private lateinit var writer: ContactWriter
    private lateinit var body: LinearLayout
    private val phones = ArrayList<PhoneRow>()
    private val emails = ArrayList<EmailRow>()
    private lateinit var phoneList: LinearLayout
    private lateinit var emailList: LinearLayout
    private lateinit var given: InkEditText
    private lateinit var family: InkEditText
    private var original: EditableContact? = null
    private var account: AccountRef = ContactWriter.LOCAL
    private var deleteArmed = false
    private lateinit var error: InkTextView

    private val contactId get() = intent.getLongExtra(EXTRA_CONTACT_ID, -1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        writer = ContactWriter(contentResolver)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(PADDING_DP)
            setPadding(padding, 0, padding, padding)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkButton(this@ContactEditActivity).apply {
                icon = R.drawable.ic_arrow_back
                contentDescription = getString(R.string.back)
                isBordered = false
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(ScrollView(this@ContactEditActivity).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(body)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        InkWindow.setContent(this, root)
        if (PERMISSIONS.all(::hasPermission)) load() else requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return
        if (PERMISSIONS.all(::hasPermission)) load() else finish()
    }

    private fun load() {
        val id = contactId
        val number = intent.getStringExtra(EXTRA_NUMBER)
        loader.execute {
            val result = runCatching {
                val accounts = writer.writableAccounts()
                accounts to if (id >= 0) writer.load(id, accounts) else null
            }
            main.post {
                if (isDestroyed) return@post
                val (accounts, loaded) = result.getOrNull() ?: return@post showNotEditable()
                if (id >= 0 && loaded == null) showNotEditable() else show(loaded, accounts, number)
            }
        }
    }

    private fun showNotEditable() {
        body.removeAllViews()
        body.addView(InkTextView(this).apply { setText(R.string.contact_not_editable) })
    }

    private fun show(contact: EditableContact?, accounts: List<AccountRef>, number: String?) {
        original = contact
        body.removeAllViews()
        body.addView(InkTextView(this).apply {
            setText(if (contact == null) R.string.contact_new else R.string.contact_edit)
            textScale = InkStyle.TITLE_SCALE
            isBold = true
        })
        if (contact == null) {
            val remembered = inkPrefs().getString(ContactWriter.PREF_DEFAULT_ACCOUNT, null)?.let(AccountRef::decode)
            account = remembered?.takeIf { it in accounts } ?: accounts.first()
            val labels = AccountLabels(this)
            body.addSection(getString(R.string.contact_account))
            accounts.forEach { option ->
                body.addView(InkButton(this).apply {
                    text = labels.label(option)
                    textScale = InkStyle.SECONDARY_SCALE
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    isSelected = option == account
                    setOnClickListener {
                        account = option
                        (parent as LinearLayout).let { group ->
                            (0 until group.childCount).map(group::getChildAt).filterIsInstance<InkButton>()
                                .filter { it.tag == ACCOUNT_TAG }.forEach { it.isSelected = it === this }
                        }
                    }
                    tag = ACCOUNT_TAG
                }, rowParams())
            }
        } else {
            account = contact.account
        }
        given = field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS).apply { setText(contact?.givenName) }
        family = field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS).apply { setText(contact?.familyName) }
        body.addView(label(R.string.contact_given_name), rowParams().apply { topMargin = dp(SECTION_GAP_DP) })
        body.addView(given, rowParams())
        body.addView(label(R.string.contact_family_name), rowParams())
        body.addView(family, rowParams())

        body.addSection(getString(R.string.card_phones))
        phoneList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(phoneList)
        phones.clear()
        contact?.phones?.forEach { addPhone(it.dataId, it.number, it.type) }
        if (contact == null) addPhone(null, number.orEmpty(), Phone.TYPE_MOBILE)
        body.addView(addButton(R.string.contact_add_phone) { addPhone(null, "", Phone.TYPE_MOBILE) }, rowParams())

        body.addSection(getString(R.string.card_emails))
        emailList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(emailList)
        emails.clear()
        contact?.emails?.forEach { addEmail(it.dataId, it.address) }
        body.addView(addButton(R.string.contact_add_email) { addEmail(null, "") }, rowParams())

        error = InkTextView(this).apply {
            isBold = true
            visibility = View.GONE
        }
        body.addView(error, rowParams().apply { topMargin = dp(SECTION_GAP_DP) })
        body.addView(InkButton(this).apply {
            setText(R.string.save)
            icon = R.drawable.ic_edit
            iconPosition = InkButton.IconPosition.START
            iconSizeDp = ICON_DP
            setOnClickListener { save() }
        }, rowParams())
        if (contact != null) {
            body.addView(InkButton(this).apply {
                setText(R.string.contact_delete)
                icon = R.drawable.ic_delete
                iconPosition = InkButton.IconPosition.START
                iconSizeDp = ICON_DP
                setOnClickListener {
                    if (deleteArmed) delete() else {
                        deleteArmed = true
                        setText(R.string.contact_delete_confirm)
                    }
                }
            }, rowParams())
        }
    }

    private fun addPhone(dataId: Long?, number: String, type: Int) {
        val field = field(InputType.TYPE_CLASS_PHONE).apply { setText(number) }
        val row = PhoneRow(dataId, field, type)
        phones += row
        phoneList.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(field, rowParams())
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(InkButton(context).apply {
                    text = typeLabel(row.type)
                    textScale = InkStyle.SECONDARY_SCALE
                    setOnClickListener {
                        row.type = ContactDiff.nextPhoneType(row.type)
                        text = typeLabel(row.type)
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(removeButton { phoneList.removeView(this@apply.parent as View); phones.remove(row) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(GAP_DP) })
            }, rowParams())
        })
    }

    private fun addEmail(dataId: Long?, address: String) {
        val field = field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS).apply { setText(address) }
        val row = EmailRow(dataId, field)
        emails += row
        emailList.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(field, rowParams())
            addView(removeButton { emailList.removeView(this); emails.remove(row) }, rowParams())
        })
    }

    private fun removeButton(onClick: () -> Unit) = InkButton(this).apply {
        setText(R.string.remove)
        textScale = InkStyle.SECONDARY_SCALE
        icon = R.drawable.ic_close
        iconPosition = InkButton.IconPosition.START
        iconSizeDp = ICON_DP
        setOnClickListener { onClick() }
    }

    private fun addButton(text: Int, onClick: () -> Unit) = InkButton(this).apply {
        setText(text)
        textScale = InkStyle.SECONDARY_SCALE
        icon = R.drawable.ic_add
        iconPosition = InkButton.IconPosition.START
        iconSizeDp = ICON_DP
        setOnClickListener { onClick() }
    }

    private fun label(text: Int) = InkTextView(this).apply {
        setText(text)
        textScale = InkStyle.SECONDARY_SCALE
    }

    private fun field(type: Int) = InkEditText(this).apply {
        inputType = type
        imeOptions = EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        isBordered = true
    }

    private fun typeLabel(type: Int) = Phone.getTypeLabel(resources, type, null).toString()

    private fun edited() = EditableContact(
        rawContactId = original?.rawContactId,
        account = account,
        nameDataId = original?.nameDataId,
        givenName = given.text.toString(),
        familyName = family.text.toString(),
        phones = phones.map { EditablePhone(it.dataId, it.field.text.toString(), it.type) },
        emails = emails.map { EditableEmail(it.dataId, it.field.text.toString()) },
    )

    private fun save() {
        val edited = edited()
        if (edited.isEmpty) return
        val before = original
        if (before == null) inkPrefs().edit { putString(ContactWriter.PREF_DEFAULT_ACCOUNT, account.encode()) }
        loader.execute {
            val result = runCatching { writer.saveAndGetContactId(before, edited) }
            main.post {
                if (isDestroyed) return@post
                if (result.isFailure) return@post showError(R.string.contact_save_failed)
                result.getOrNull()?.takeIf { before == null }?.let { startActivity(ContactCardActivity.intent(this, it)) }
                finish()
            }
        }
    }

    private fun delete() {
        val id = contactId
        loader.execute {
            val result = runCatching { writer.delete(id) }
            main.post {
                if (isDestroyed) return@post
                if (result.isFailure) return@post showError(R.string.contact_delete_failed)
                setResult(RESULT_DELETED)
                finish()
            }
        }
    }

    private fun showError(text: Int) {
        error.setText(text)
        error.visibility = View.VISIBLE
    }

    private fun rowParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(GAP_DP)
    }

    private fun dp(value: Float) = (value * density).roundToInt()

    companion object {
        const val RESULT_DELETED = RESULT_FIRST_USER
        private const val EXTRA_CONTACT_ID = "contact_id"
        private const val EXTRA_NUMBER = "number"
        private const val ACCOUNT_TAG = "account"
        private const val REQUEST_PERMISSIONS = 51
        private val PERMISSIONS = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        private const val PADDING_DP = 16f
        private const val GAP_DP = 8f
        private const val SECTION_GAP_DP = 20f
        private const val ICON_DP = 20f
        private val loader = Executors.newSingleThreadExecutor()

        fun intentForNew(context: Context, number: String? = null): Intent =
            Intent(context, ContactEditActivity::class.java).putExtra(EXTRA_NUMBER, number)

        fun intentForEdit(context: Context, contactId: Long): Intent =
            Intent(context, ContactEditActivity::class.java).putExtra(EXTRA_CONTACT_ID, contactId)
    }
}
