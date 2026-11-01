package io.github.andreygladkov.inkphone.vcard

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import io.github.andreygladkov.inkphone.calllog.CallLogGrouping
import io.github.andreygladkov.inkphone.contacts.AccountRef

class ContactCards(private val resolver: ContentResolver) {

    sealed interface Scope {
        data object All : Scope
        data class Account(val account: AccountRef) : Scope
        data class Contacts(val ids: Set<Long>) : Scope
    }

    fun read(scope: Scope): List<CardData> {
        val rawFilter = (scope as? Scope.Account)?.let { rawContactsOf(it.account) }
        val groups = LinkedHashMap<Long, CardData>()
        val selection = buildString {
            append("${Data.MIMETYPE} IN (${MIME_TYPES.joinToString { "?" }})")
            if (scope is Scope.Contacts) append(" AND ${Data.CONTACT_ID} IN (${scope.ids.joinToString()})")
        }
        resolver.query(Data.CONTENT_URI, null, selection, MIME_TYPES.toTypedArray(), "${Data.CONTACT_ID}, ${Data.IS_SUPER_PRIMARY} DESC")?.use { cursor ->
            val rows = Rows(cursor)
            while (cursor.moveToNext()) {
                val contactId = rows.long(Data.CONTACT_ID)
                val rawId = rows.long(Data.RAW_CONTACT_ID)
                when (scope) {
                    is Scope.Account -> if (rawId !in rawFilter!!) continue
                    is Scope.Contacts -> if (contactId !in scope.ids) continue
                    Scope.All -> Unit
                }
                val key = if (scope is Scope.Account) rawId else contactId
                groups[key] = add(groups[key] ?: CardData(), rows)
            }
        }
        return groups.values.toList()
    }

    fun existing(): List<ExistingContact> {
        val names = HashMap<Long, String>()
        resolver.query(Contacts.CONTENT_URI, arrayOf(Contacts._ID, Contacts.DISPLAY_NAME_PRIMARY), null, null, null)?.use {
            while (it.moveToNext()) names[it.getLong(0)] = it.getString(1).orEmpty()
        }
        val numbers = HashMap<Long, MutableList<String>>()
        val emails = HashMap<Long, MutableList<String>>()
        resolver.query(Data.CONTENT_URI, arrayOf(Data.CONTACT_ID, Data.MIMETYPE, Data.DATA1), "${Data.MIMETYPE} IN (?,?)",
            arrayOf(Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE), null)?.use {
            while (it.moveToNext()) {
                val value = it.getString(2) ?: continue
                val target = if (it.getString(1) == Phone.CONTENT_ITEM_TYPE) numbers else emails
                target.getOrPut(it.getLong(0)) { ArrayList() } += value
            }
        }
        return names.map { (id, name) -> ExistingContact(id, name, numbers[id].orEmpty(), emails[id].orEmpty()) }
    }

    fun insert(cards: List<CardData>, account: AccountRef, onProgress: (Int) -> Unit) {
        cards.chunked(BATCH_CONTACTS).forEach { chunk ->
            val ops = ArrayList<ContentProviderOperation>()
            chunk.forEach { card ->
                val rawIndex = ops.size
                ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                    .withValue(RawContacts.ACCOUNT_NAME, account.name)
                    .build()
                ops += dataOperations(card) { it.withValueBackReference(Data.RAW_CONTACT_ID, rawIndex) }
            }
            resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            onProgress(chunk.size)
        }
    }

    fun merge(card: CardData, into: ExistingContact, writable: List<AccountRef>, fallback: AccountRef) {
        val missing = ImportPlan.missingParts(card, into.numbers, into.emails)
        if (missing.phones.isEmpty() && missing.emails.isEmpty()) return
        val rawId = writableRawContact(into.id, writable)
        if (rawId == null) {
            insert(listOf(card.copy(phones = missing.phones, emails = missing.emails, addresses = emptyList(), note = "")), fallback) {}
            return
        }
        val onlyNew = CardData(phones = missing.phones, emails = missing.emails)
        resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(dataOperations(onlyNew, includeName = false) { it.withValue(Data.RAW_CONTACT_ID, rawId) }))
    }

    private fun writableRawContact(contactId: Long, writable: List<AccountRef>): Long? {
        resolver.query(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0", arrayOf(contactId.toString()), null)?.use {
            while (it.moveToNext()) if (AccountRef(it.getString(1), it.getString(2)) in writable) return it.getLong(0)
        }
        return null
    }

    private fun rawContactsOf(account: AccountRef): Set<Long> {
        val ids = HashSet<Long>()
        val selection = buildString {
            append("${RawContacts.DELETED}=0 AND ")
            append(if (account.type == null) "${RawContacts.ACCOUNT_TYPE} IS NULL" else "${RawContacts.ACCOUNT_TYPE}=? AND ${RawContacts.ACCOUNT_NAME}=?")
        }
        val args = if (account.type == null) null else arrayOf(account.type, account.name.orEmpty())
        resolver.query(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID), selection, args, null)?.use {
            while (it.moveToNext()) ids += it.getLong(0)
        }
        return ids
    }

    private fun add(card: CardData, rows: Rows): CardData = when (rows.string(Data.MIMETYPE)) {
        StructuredName.CONTENT_ITEM_TYPE -> if (card.given.isNotBlank() || card.family.isNotBlank() || card.formattedName.isNotBlank()) card else card.copy(
            formattedName = rows.string(StructuredName.DISPLAY_NAME),
            prefix = rows.string(StructuredName.PREFIX),
            given = rows.string(StructuredName.GIVEN_NAME),
            middle = rows.string(StructuredName.MIDDLE_NAME),
            family = rows.string(StructuredName.FAMILY_NAME),
            suffix = rows.string(StructuredName.SUFFIX),
        )
        Nickname.CONTENT_ITEM_TYPE -> card.copy(nickname = card.nickname.ifBlank { rows.string(Nickname.NAME) })
        Phone.CONTENT_ITEM_TYPE -> {
            val number = rows.string(Phone.NUMBER)
            if (number.isBlank() || card.phones.any { CallLogGrouping.sameNumber(it.value, number) }) card
            else card.copy(phones = card.phones + TypedValue(number, rows.int(Phone.TYPE), rows.string(Phone.LABEL).ifBlank { null }))
        }
        Email.CONTENT_ITEM_TYPE -> {
            val address = rows.string(Email.ADDRESS)
            if (address.isBlank() || card.emails.any { it.value.equals(address, ignoreCase = true) }) card
            else card.copy(emails = card.emails + TypedValue(address, rows.int(Email.TYPE)))
        }
        StructuredPostal.CONTENT_ITEM_TYPE -> {
            val address = PostalAddress(
                street = rows.string(StructuredPostal.STREET).ifBlank { rows.string(StructuredPostal.FORMATTED_ADDRESS) },
                city = rows.string(StructuredPostal.CITY),
                region = rows.string(StructuredPostal.REGION),
                postcode = rows.string(StructuredPostal.POSTCODE),
                country = rows.string(StructuredPostal.COUNTRY),
                type = rows.int(StructuredPostal.TYPE),
            )
            if (address in card.addresses) card else card.copy(addresses = card.addresses + address)
        }
        Organization.CONTENT_ITEM_TYPE -> card.copy(
            organization = card.organization.ifBlank { rows.string(Organization.COMPANY) },
            title = card.title.ifBlank { rows.string(Organization.TITLE) },
        )
        Event.CONTENT_ITEM_TYPE -> if (rows.int(Event.TYPE) == Event.TYPE_BIRTHDAY) card.copy(birthday = card.birthday.ifBlank { rows.string(Event.START_DATE) }) else card
        Note.CONTENT_ITEM_TYPE -> card.copy(note = listOf(card.note, rows.string(Note.NOTE)).filter { it.isNotBlank() }.distinct().joinToString("\n"))
        Website.CONTENT_ITEM_TYPE -> rows.string(Website.URL).let { url -> if (url.isBlank() || url in card.urls) card else card.copy(urls = card.urls + url) }
        else -> card
    }

    private fun dataOperations(
        card: CardData,
        includeName: Boolean = true,
        link: (ContentProviderOperation.Builder) -> ContentProviderOperation.Builder,
    ): List<ContentProviderOperation> = buildList {
        fun row(mime: String, vararg values: Pair<String, Any?>) {
            add(link(ContentProviderOperation.newInsert(Data.CONTENT_URI)).withValue(Data.MIMETYPE, mime)
                .apply { values.forEach { (column, value) -> withValue(column, value) } }.build())
        }
        if (includeName) {
            val structured = listOf(card.prefix, card.given, card.middle, card.family, card.suffix).any { it.isNotBlank() }
            if (structured) {
                row(StructuredName.CONTENT_ITEM_TYPE, StructuredName.PREFIX to card.prefix.ifBlank { null },
                    StructuredName.GIVEN_NAME to card.given.ifBlank { null }, StructuredName.MIDDLE_NAME to card.middle.ifBlank { null },
                    StructuredName.FAMILY_NAME to card.family.ifBlank { null }, StructuredName.SUFFIX to card.suffix.ifBlank { null })
            } else if (card.displayName.isNotBlank()) {
                row(StructuredName.CONTENT_ITEM_TYPE, StructuredName.DISPLAY_NAME to card.displayName)
            }
        }
        if (card.nickname.isNotBlank()) row(Nickname.CONTENT_ITEM_TYPE, Nickname.NAME to card.nickname)
        card.phones.forEach { row(Phone.CONTENT_ITEM_TYPE, Phone.NUMBER to it.value, Phone.TYPE to it.type, Phone.LABEL to it.label) }
        card.emails.forEach { row(Email.CONTENT_ITEM_TYPE, Email.ADDRESS to it.value, Email.TYPE to it.type) }
        card.addresses.forEach {
            row(StructuredPostal.CONTENT_ITEM_TYPE, StructuredPostal.STREET to it.street.ifBlank { null }, StructuredPostal.CITY to it.city.ifBlank { null },
                StructuredPostal.REGION to it.region.ifBlank { null }, StructuredPostal.POSTCODE to it.postcode.ifBlank { null },
                StructuredPostal.COUNTRY to it.country.ifBlank { null }, StructuredPostal.TYPE to it.type)
        }
        if (card.organization.isNotBlank() || card.title.isNotBlank()) {
            row(Organization.CONTENT_ITEM_TYPE, Organization.COMPANY to card.organization.ifBlank { null }, Organization.TITLE to card.title.ifBlank { null })
        }
        if (card.birthday.isNotBlank()) row(Event.CONTENT_ITEM_TYPE, Event.START_DATE to card.birthday, Event.TYPE to Event.TYPE_BIRTHDAY)
        if (card.note.isNotBlank()) row(Note.CONTENT_ITEM_TYPE, Note.NOTE to card.note)
        card.urls.forEach { row(Website.CONTENT_ITEM_TYPE, Website.URL to it, Website.TYPE to Website.TYPE_OTHER) }
    }

    private class Rows(private val cursor: Cursor) {
        fun string(column: String): String = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let { cursor.getString(it) }.orEmpty()
        fun int(column: String): Int = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let { cursor.getInt(it) } ?: 0
        fun long(column: String): Long = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0
    }

    private companion object {
        // Keeps each batch well under the provider's 500-operation limit even for contacts with many fields.
        const val BATCH_CONTACTS = 20
        val MIME_TYPES = listOf(
            StructuredName.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE,
            StructuredPostal.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE, Event.CONTENT_ITEM_TYPE, Note.CONTENT_ITEM_TYPE,
            Website.CONTENT_ITEM_TYPE,
        )
    }
}
