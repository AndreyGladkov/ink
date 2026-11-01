package io.github.andreygladkov.inkphone.contacts

import android.provider.ContactsContract.CommonDataKinds.Phone

data class EditablePhone(val dataId: Long?, val number: String, val type: Int = Phone.TYPE_MOBILE)

data class EditableEmail(val dataId: Long?, val address: String)

data class EditableContact(
    val rawContactId: Long?,
    val account: AccountRef,
    val nameDataId: Long? = null,
    val givenName: String = "",
    val familyName: String = "",
    val phones: List<EditablePhone> = emptyList(),
    val emails: List<EditableEmail> = emptyList(),
) {
    val isEmpty: Boolean
        get() = givenName.isBlank() && familyName.isBlank() && phones.none { it.number.isNotBlank() } && emails.none { it.address.isNotBlank() }
}

sealed interface ContactChange {
    data class SetName(val dataId: Long?, val given: String, val family: String) : ContactChange
    data class InsertPhone(val number: String, val type: Int) : ContactChange
    data class UpdatePhone(val dataId: Long, val number: String, val type: Int) : ContactChange
    data class InsertEmail(val address: String) : ContactChange
    data class UpdateEmail(val dataId: Long, val address: String) : ContactChange
    data class DeleteData(val dataId: Long) : ContactChange
}

object ContactDiff {
    val PHONE_TYPES = listOf(Phone.TYPE_MOBILE, Phone.TYPE_HOME, Phone.TYPE_WORK, Phone.TYPE_OTHER)

    fun changes(original: EditableContact?, edited: EditableContact): List<ContactChange> = buildList {
        val given = edited.givenName.trim()
        val family = edited.familyName.trim()
        if (original == null || original.givenName.trim() != given || original.familyName.trim() != family) {
            add(ContactChange.SetName(original?.nameDataId, given, family))
        }

        val phones = edited.phones.map { it.copy(number = it.number.trim()) }
        val keptPhones = phones.filter { it.number.isNotEmpty() }
        original?.phones.orEmpty().forEach { old ->
            val now = keptPhones.firstOrNull { it.dataId == old.dataId }
            when {
                now == null -> add(ContactChange.DeleteData(old.dataId!!))
                now.number != old.number || now.type != old.type -> add(ContactChange.UpdatePhone(old.dataId!!, now.number, now.type))
            }
        }
        keptPhones.filter { it.dataId == null }.forEach { add(ContactChange.InsertPhone(it.number, it.type)) }

        val emails = edited.emails.map { it.copy(address = it.address.trim()) }
        val keptEmails = emails.filter { it.address.isNotEmpty() }
        original?.emails.orEmpty().forEach { old ->
            val now = keptEmails.firstOrNull { it.dataId == old.dataId }
            when {
                now == null -> add(ContactChange.DeleteData(old.dataId!!))
                now.address != old.address -> add(ContactChange.UpdateEmail(old.dataId!!, now.address))
            }
        }
        keptEmails.filter { it.dataId == null }.forEach { add(ContactChange.InsertEmail(it.address)) }
    }

    fun nextPhoneType(type: Int): Int = PHONE_TYPES[(PHONE_TYPES.indexOf(type) + 1) % PHONE_TYPES.size]
}
