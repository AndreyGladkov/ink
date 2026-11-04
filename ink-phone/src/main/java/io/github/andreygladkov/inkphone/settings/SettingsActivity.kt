package io.github.andreygladkov.inkphone.settings

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.provider.ContactsContract.RawContacts
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.edit
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.addSection
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

class SettingsActivity : Activity() {
    private val prefs: SharedPreferences get() = inkPrefs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val padding = (PADDING_DP * density).roundToInt()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, padding)
        }
        body.addView(InkTextView(this).apply {
            setText(R.string.settings)
            textScale = InkStyle.TITLE_SCALE
            isBold = true
        })
        addDisplay(body)
        addHaptics(body)
        addCalls(body)
        addContacts(body)
        addImportExport(body)
        addA9(body)
        addAbout(body)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkButton(this@SettingsActivity).apply {
                icon = R.drawable.ic_arrow_back
                contentDescription = getString(R.string.back)
                isBordered = false
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(ScrollView(this@SettingsActivity).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(body)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        InkWindow.setContent(this, root)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CallLauncher.REQUEST_DIALER_ROLE) recreate()
    }

    private fun addDisplay(body: LinearLayout) {
        val style = InkStyle.of(this)
        body.addSection(getString(InkR.string.settings_display))
        body.addView(InkSettings.textSize(this))
        body.addView(InkChoiceRow(this, getString(R.string.settings_dialer_number_size),
            DialerScreen.NUMBER_SCALES.map { it to (style.textSizeSp * it).roundToInt().toString() },
            DialerScreen.numberScale(prefs),
        ) { scale -> DialerScreen.saveNumberScale(prefs, scale) })
        body.addView(InkChoiceRow(this, getString(R.string.settings_keypad_letters),
            listOf(
                KeypadLetters.AUTO to getString(R.string.keypad_letters_auto),
                KeypadLetters.LATIN to getString(R.string.keypad_letters_latin),
                KeypadLetters.LATIN_CYRILLIC to getString(R.string.keypad_letters_latin_cyrillic),
            ),
            KeypadLetters.from(prefs),
        ) { letters -> prefs.edit { putString(KeypadLetters.PREF, letters.name) } })
        listOf(InkSettings::weight, InkSettings::lists, InkSettings::cornerRadius, InkSettings::colors, InkSettings::tabLabels).forEach { body.addView(it(this)) }
    }

    private fun addHaptics(body: LinearLayout) = InkSettings.addHaptics(this, body, listOf(
        HapticChannel.KEYS to getString(R.string.settings_haptic_keys),
        HapticChannel.BUTTONS to getString(InkR.string.settings_haptic_buttons),
    ))

    private fun addContacts(body: LinearLayout) {
        val options = ContactListOptions.from(prefs)
        body.addSection(getString(R.string.tab_contacts))
        body.addView(InkChoiceRow(this, getString(R.string.settings_without_number),
            listOf(false to getString(R.string.show), true to getString(R.string.hide)), options.onlyWithPhone,
        ) { only -> prefs.edit { putBoolean(ContactListOptions.PREF_ONLY_WITH_PHONE, only) } })
        body.addView(InkChoiceRow(this, getString(R.string.settings_name_order),
            listOf(NameOrder.GIVEN_FIRST to getString(R.string.name_order_given_first), NameOrder.FAMILY_FIRST to getString(R.string.name_order_family_first)),
            options.nameOrder,
        ) { order -> prefs.edit { putString(ContactListOptions.PREF_NAME_ORDER, order.name) } })
        addShownAccounts(body, options)
    }

    private fun addShownAccounts(body: LinearLayout, options: ContactListOptions) {
        if (!canReadContacts()) return
        val accounts = LinkedHashSet<AccountRef>()
        contentResolver.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME), "${RawContacts.DELETED}=0", null, null)?.use {
            while (it.moveToNext()) accounts += AccountRef(it.getString(0), it.getString(1))
        }
        if (accounts.size < 2) return
        val hidden = options.hiddenAccounts.toMutableSet()
        val labels = AccountLabels(this)
        body.addView(InkTextView(this).apply {
            setText(R.string.settings_shown_accounts)
            setPadding(0, gap() * 3, 0, 0)
        })
        accounts.forEach { account ->
            body.addView(InkButton(this).apply {
                text = labels.label(account)
                textScale = InkStyle.SECONDARY_SCALE
                maxLines = 1
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                iconPosition = InkButton.IconPosition.START
                iconSizeDp = ICON_DP
                fun refresh() {
                    icon = if (account in hidden) R.drawable.ic_check_box_blank else R.drawable.ic_check_box
                }
                refresh()
                setOnClickListener {
                    if (!hidden.add(account)) hidden.remove(account)
                    prefs.edit { putStringSet(ContactListOptions.PREF_HIDDEN_ACCOUNTS, hidden.map { it.encode() }.toSet()) }
                    refresh()
                }
            }, rowParams())
        }
    }

    private fun addCalls(body: LinearLayout) {
        body.addSection(getString(R.string.settings_calls))
        if (CallLauncher.isDefaultDialer(this)) {
            body.addView(InkTextView(this).apply {
                setText(R.string.default_dialer_yes)
                setPadding(0, gap(), 0, 0)
            })
        } else {
            body.addView(wideButton(R.string.make_default, R.drawable.ic_call) { CallLauncher.requestDefaultDialer(this) }, rowParams())
        }
        body.addView(wideButton(R.string.settings_sim, R.drawable.ic_settings) {
            startActivity(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS))
        }, rowParams())
        addQuickReplies(body)
    }

    private fun addQuickReplies(body: LinearLayout) {
        val replies = QuickReplies.of(this)
        body.addView(InkTextView(this).apply {
            setText(R.string.settings_quick_replies)
            setPadding(0, gap() * 2, 0, 0)
        })
        replies.all().forEachIndexed { index, text ->
            body.addView(InkEditText(this).apply {
                isBordered = true
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                contentDescription = getString(R.string.settings_quick_reply, index + 1)
                setText(text)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) = replies.set(index, s.toString())
                })
            }, rowParams())
        }
    }

    private fun addA9(body: LinearLayout) = InkSettings.addA9(this, body, listOf(
        InkSettings.onOff(this, R.string.a9_clear_after_call, prefs.getBoolean(CallPanelClear.PREF_CLEAR_AFTER_CALL, false)) { on ->
            prefs.edit { putBoolean(CallPanelClear.PREF_CLEAR_AFTER_CALL, on) }
        },
    ))

    private fun addAbout(body: LinearLayout) {
        body.addSection(getString(R.string.about))
        body.addView(wideButton(R.string.about, 0) { startActivity(Intent(this, AboutActivity::class.java)) }, rowParams())
    }

    private fun wideButton(text: Int, icon: Int, onClick: () -> Unit) = InkButton(this).apply {
        setText(text)
        if (icon != 0) {
            this.icon = icon
            iconPosition = InkButton.IconPosition.START
            iconSizeDp = ICON_DP
        }
        setOnClickListener { onClick() }
    }

    private fun rowParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = gap()
    }

    private fun gap() = (GAP_DP * resources.displayMetrics.density).roundToInt()

    private fun addImportExport(body: LinearLayout) {
        body.addSection(getString(R.string.settings_import_export))
        listOf(
            Triple(R.string.export_contacts, R.drawable.ic_download, ExportActivity::class.java),
            Triple(R.string.import_contacts, R.drawable.ic_upload, ImportActivity::class.java),
        ).forEach { (title, icon, screen) ->
            body.addView(InkButton(this).apply {
                setText(title)
                this.icon = icon
                iconPosition = InkButton.IconPosition.START
                iconSizeDp = ICON_DP
                setOnClickListener { startActivity(Intent(this@SettingsActivity, screen)) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (GAP_DP * resources.displayMetrics.density).roundToInt()
            })
        }
    }

    private companion object {
        const val PADDING_DP = 16f
        const val ICON_DP = 20f
        const val GAP_DP = 8f
    }
}
