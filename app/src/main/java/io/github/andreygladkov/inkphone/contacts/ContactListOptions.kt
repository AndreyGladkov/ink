package io.github.andreygladkov.inkphone.contacts

import android.content.SharedPreferences

enum class NameOrder { GIVEN_FIRST, FAMILY_FIRST }

data class ContactListOptions(
    val nameOrder: NameOrder = NameOrder.GIVEN_FIRST,
    val onlyWithPhone: Boolean = true,
    val hiddenAccounts: Set<AccountRef> = emptySet(),
) {
    companion object {
        const val PREF_NAME_ORDER = "contacts_name_order"
        const val PREF_ONLY_WITH_PHONE = "contacts_only_with_phone"
        const val PREF_HIDDEN_ACCOUNTS = "contacts_hidden_accounts"

        fun from(prefs: SharedPreferences) = ContactListOptions(
            nameOrder = prefs.getString(PREF_NAME_ORDER, null)
                ?.let { name -> NameOrder.entries.find { it.name == name } } ?: NameOrder.GIVEN_FIRST,
            onlyWithPhone = prefs.getBoolean(PREF_ONLY_WITH_PHONE, true),
            hiddenAccounts = prefs.getStringSet(PREF_HIDDEN_ACCOUNTS, emptySet())!!.map(AccountRef::decode).toSet(),
        )
    }
}
