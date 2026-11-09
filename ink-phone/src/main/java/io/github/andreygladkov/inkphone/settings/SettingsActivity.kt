package io.github.andreygladkov.inkphone.settings

import android.content.Intent
import android.content.SharedPreferences
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.provider.ContactsContract.RawContacts
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.core.content.edit
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkPagedActivity
import io.github.andreygladkov.ink.ui.InkPagedItems
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.call.CallLauncher
import io.github.andreygladkov.inkphone.call.CallPanelClear
import io.github.andreygladkov.inkphone.call.QuickReplies
import io.github.andreygladkov.inkphone.canReadContacts
import io.github.andreygladkov.inkphone.contacts.AccountLabels
import io.github.andreygladkov.inkphone.contacts.AccountRef
import io.github.andreygladkov.inkphone.contacts.ContactListOptions
import io.github.andreygladkov.inkphone.contacts.NameOrder
import io.github.andreygladkov.inkphone.dialer.DialerScreen
import io.github.andreygladkov.inkphone.dialer.KeypadLetters
import io.github.andreygladkov.inkphone.vcard.ExportActivity
import io.github.andreygladkov.inkphone.vcard.ImportActivity
import kotlin.math.roundToInt
import io.github.andreygladkov.ink.R as InkR

class SettingsActivity : InkPagedActivity() {
    private val prefs: SharedPreferences get() = inkPrefs()
    private val replyDrafts = mutableMapOf<Int, String>()

    override fun headerTitle(): String = getString(R.string.settings)

    override fun InkPagedItems.build() {
        addDisplay()
        InkSettings.addHaptics(this@SettingsActivity, this, listOf(
            HapticChannel.KEYS to getString(R.string.settings_haptic_keys),
            HapticChannel.BUTTONS to getString(InkR.string.settings_haptic_buttons),
        ))
        addCalls()
        addContacts()
        addImportExport()
        InkSettings.addA9(this@SettingsActivity, this, listOf(::clearAfterCall))
        InkSettings.addAbout(this@SettingsActivity, this, AboutActivity::class.java)
    }

    override fun outsideState() = CallLauncher.isDefaultDialer(this) to canReadContacts()

    private fun clearAfterCall() = InkSettings.onOff(this, R.string.a9_clear_after_call, prefs.getBoolean(CallPanelClear.PREF_CLEAR_AFTER_CALL, false)) { on ->
        prefs.edit { putBoolean(CallPanelClear.PREF_CLEAR_AFTER_CALL, on) }
    }

    private fun InkPagedItems.addDisplay() {
        val activity = this@SettingsActivity
        section(getString(InkR.string.settings_display))
        item { InkSettings.textSize(activity) }
        item {
            InkChoiceRow(activity, getString(R.string.settings_dialer_number_size),
                DialerScreen.NUMBER_SCALES.map { it to (InkStyle.of(activity).textSizeSp * it).roundToInt().toString() },
                DialerScreen.numberScale(prefs),
            ) { scale -> DialerScreen.saveNumberScale(prefs, scale) }
        }
        item {
            InkChoiceRow(activity, getString(R.string.settings_keypad_letters),
                listOf(
                    KeypadLetters.AUTO to getString(R.string.keypad_letters_auto),
                    KeypadLetters.LATIN to getString(R.string.keypad_letters_latin),
                    KeypadLetters.LATIN_CYRILLIC to getString(R.string.keypad_letters_latin_cyrillic),
                ),
                KeypadLetters.from(prefs),
            ) { letters -> prefs.edit { putString(KeypadLetters.PREF, letters.name) } }
        }
        listOf(InkSettings::weight, InkSettings::lists, InkSettings::cornerRadius, InkSettings::colors, InkSettings::tabLabels).forEach { item { it(activity) } }
    }

    private fun InkPagedItems.addContacts() {
        section(getString(R.string.tab_contacts))
        item {
            InkChoiceRow(this@SettingsActivity, getString(R.string.settings_without_number),
                listOf(false to getString(R.string.show), true to getString(R.string.hide)), ContactListOptions.from(prefs).onlyWithPhone,
            ) { only -> prefs.edit { putBoolean(ContactListOptions.PREF_ONLY_WITH_PHONE, only) } }
        }
        item {
            InkChoiceRow(this@SettingsActivity, getString(R.string.settings_name_order),
                listOf(NameOrder.GIVEN_FIRST to getString(R.string.name_order_given_first), NameOrder.FAMILY_FIRST to getString(R.string.name_order_family_first)),
                ContactListOptions.from(prefs).nameOrder,
            ) { order -> prefs.edit { putString(ContactListOptions.PREF_NAME_ORDER, order.name) } }
        }
        addShownAccounts()
    }

    private fun InkPagedItems.addShownAccounts() {
        if (!canReadContacts()) return
        val accounts = LinkedHashSet<AccountRef>()
        contentResolver.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME), "${RawContacts.DELETED}=0", null, null)?.use {
            while (it.moveToNext()) accounts += AccountRef(it.getString(0), it.getString(1))
        }
        if (accounts.size < 2) return
        val labels = AccountLabels(this@SettingsActivity)
        item {
            InkTextView(this@SettingsActivity).apply {
                setText(R.string.settings_shown_accounts)
                setPadding(0, gap() * 3, 0, 0)
            }
        }
        accounts.forEach { account -> item { accountToggle(account, labels.label(account)) } }
    }

    private fun accountToggle(account: AccountRef, label: String) = InkButton(this).apply {
        text = label
        textScale = InkStyle.SECONDARY_SCALE
        maxLines = 1
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        iconPosition = InkButton.IconPosition.START
        iconSizeDp = ICON_DP
        fun refresh() {
            icon = if (account in ContactListOptions.from(prefs).hiddenAccounts) R.drawable.ic_check_box_blank else R.drawable.ic_check_box
        }
        refresh()
        setOnClickListener {
            val hidden = ContactListOptions.from(prefs).hiddenAccounts.toMutableSet()
            if (!hidden.add(account)) hidden.remove(account)
            prefs.edit { putStringSet(ContactListOptions.PREF_HIDDEN_ACCOUNTS, hidden.map { it.encode() }.toSet()) }
            refresh()
        }
        layoutParams = rowParams()
    }

    private fun InkPagedItems.addCalls() {
        section(getString(R.string.settings_calls))
        if (CallLauncher.isDefaultDialer(this@SettingsActivity)) {
            item {
                InkTextView(this@SettingsActivity).apply {
                    setText(R.string.default_dialer_yes)
                    setPadding(0, gap(), 0, 0)
                }
            }
        } else {
            item { wideButton(R.string.make_default, R.drawable.ic_call) { CallLauncher.requestDefaultDialer(this@SettingsActivity) } }
        }
        item { wideButton(R.string.settings_sim, R.drawable.ic_settings) { startActivity(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)) } }
        addQuickReplies()
    }

    private fun InkPagedItems.addQuickReplies() {
        val replies = QuickReplies.of(this@SettingsActivity)
        item {
            InkTextView(this@SettingsActivity).apply {
                setText(R.string.settings_quick_replies)
                setPadding(0, gap() * 2, 0, 0)
            }
        }
        replies.all().indices.forEach { index -> item { quickReply(replies, index) } }
    }

    private fun quickReply(replies: QuickReplies, index: Int) = InkEditText(this).apply {
        isBordered = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        contentDescription = getString(R.string.settings_quick_reply, index + 1)
        setText(replyDrafts[index] ?: replies.get(index))
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                replyDrafts[index] = s.toString()
                replies.set(index, s.toString())
            }
        })
        layoutParams = rowParams()
    }

    private fun InkPagedItems.addImportExport() {
        section(getString(R.string.settings_import_export))
        item { wideButton(R.string.export_contacts, R.drawable.ic_download) { startActivity(Intent(this@SettingsActivity, ExportActivity::class.java)) } }
        item { wideButton(R.string.import_contacts, R.drawable.ic_upload) { startActivity(Intent(this@SettingsActivity, ImportActivity::class.java)) } }
    }

    private fun wideButton(text: Int, icon: Int, onClick: () -> Unit) = InkSettings.rowButton(this, getString(text), onClick).apply {
        this.icon = icon
        iconPosition = InkButton.IconPosition.START
        iconSizeDp = ICON_DP
    }

    private fun rowParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = gap()
    }

    private fun gap() = (GAP_DP * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val ICON_DP = 20f
        const val GAP_DP = 8f
    }
}
