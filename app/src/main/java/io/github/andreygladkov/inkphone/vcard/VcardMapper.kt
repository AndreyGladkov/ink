package io.github.andreygladkov.inkphone.vcard

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.AddressType
import ezvcard.parameter.EmailType
import ezvcard.parameter.TelephoneType
import ezvcard.property.Address
import ezvcard.property.Birthday
import ezvcard.property.StructuredName
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

object VcardMapper {
    private val FULL_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val YEARLESS_DATE = Regex("""--(\d{2})-(\d{2})""")

    fun write(cards: List<CardData>, out: OutputStream) {
        Ezvcard.write(cards.map(::toVcard)).version(VCardVersion.V3_0).prodId(false).go(out)
    }

    fun read(input: InputStream): List<CardData> = Ezvcard.parse(input).all().map(::fromVcard)

    fun toVcard(card: CardData): VCard = VCard().apply {
        setFormattedName(card.displayName)
        structuredName = StructuredName().apply {
            given = card.given.ifBlank { null }
            family = card.family.ifBlank { null }
            if (card.middle.isNotBlank()) additionalNames += card.middle
            if (card.prefix.isNotBlank()) prefixes += card.prefix
            if (card.suffix.isNotBlank()) suffixes += card.suffix
        }
        if (card.nickname.isNotBlank()) setNickname(card.nickname)
        card.phones.forEach { addTelephoneNumber(it.value, *telephoneTypes(it.type)) }
        card.emails.forEach { addEmail(it.value, *emailTypes(it.type)) }
        card.addresses.forEach { address ->
            addAddress(Address().apply {
                streetAddress = address.street.ifBlank { null }
                locality = address.city.ifBlank { null }
                region = address.region.ifBlank { null }
                postalCode = address.postcode.ifBlank { null }
                country = address.country.ifBlank { null }
                addressTypes(address.type)?.let { types.add(it) }
            })
        }
        if (card.organization.isNotBlank()) setOrganization(card.organization)
        if (card.title.isNotBlank()) addTitle(card.title)
        if (YEARLESS_DATE.matches(card.birthday)) {
            // vCard 3.0 has no partial dates, so the library drops them; Google Contacts writes them like this.
            addExtendedProperty("BDAY", card.birthday)
        } else {
            birthday(card.birthday)?.let { birthday = it }
        }
        if (card.note.isNotBlank()) addNote(card.note)
        card.urls.forEach { addUrl(it) }
    }

    fun fromVcard(vcard: VCard): CardData {
        val name = vcard.structuredName
        return CardData(
            formattedName = vcard.formattedName?.value.orEmpty(),
            prefix = name?.prefixes?.joinToString(" ").orEmpty(),
            given = name?.given.orEmpty(),
            middle = name?.additionalNames?.joinToString(" ").orEmpty(),
            family = name?.family.orEmpty(),
            suffix = name?.suffixes?.joinToString(" ").orEmpty(),
            nickname = vcard.nickname?.values?.firstOrNull().orEmpty(),
            phones = vcard.telephoneNumbers.mapNotNull { tel ->
                val number = tel.text ?: tel.uri?.number ?: return@mapNotNull null
                TypedValue(number, phoneType(tel.types))
            },
            emails = vcard.emails.mapNotNull { email -> email.value?.let { TypedValue(it, emailType(email.types)) } },
            addresses = vcard.addresses.map { address ->
                PostalAddress(
                    street = address.streetAddress.orEmpty(),
                    city = address.locality.orEmpty(),
                    region = address.region.orEmpty(),
                    postcode = address.postalCode.orEmpty(),
                    country = address.country.orEmpty(),
                    type = addressType(address.types),
                )
            },
            organization = vcard.organization?.values?.joinToString(", ").orEmpty(),
            title = vcard.titles.firstOrNull()?.value.orEmpty(),
            birthday = vcard.birthday?.let(::birthdayText) ?: vcard.getExtendedProperty("BDAY")?.value.orEmpty(),
            note = vcard.notes.joinToString("\n") { it.value.orEmpty() }.trim(),
            urls = vcard.urls.mapNotNull { it.value },
        )
    }

    private fun telephoneTypes(type: Int): Array<TelephoneType> = when (type) {
        Phone.TYPE_MOBILE -> arrayOf(TelephoneType.CELL)
        Phone.TYPE_HOME -> arrayOf(TelephoneType.HOME)
        Phone.TYPE_WORK -> arrayOf(TelephoneType.WORK)
        Phone.TYPE_FAX_WORK -> arrayOf(TelephoneType.FAX, TelephoneType.WORK)
        Phone.TYPE_FAX_HOME -> arrayOf(TelephoneType.FAX, TelephoneType.HOME)
        Phone.TYPE_PAGER -> arrayOf(TelephoneType.PAGER)
        Phone.TYPE_MAIN -> arrayOf(TelephoneType.PREF)
        else -> arrayOf(TelephoneType.VOICE)
    }

    private fun phoneType(types: List<TelephoneType>): Int = when {
        TelephoneType.FAX in types && TelephoneType.WORK in types -> Phone.TYPE_FAX_WORK
        TelephoneType.FAX in types -> Phone.TYPE_FAX_HOME
        TelephoneType.CELL in types -> Phone.TYPE_MOBILE
        TelephoneType.PAGER in types -> Phone.TYPE_PAGER
        TelephoneType.WORK in types -> Phone.TYPE_WORK
        TelephoneType.HOME in types -> Phone.TYPE_HOME
        TelephoneType.VOICE in types && types.size == 1 -> Phone.TYPE_OTHER
        else -> Phone.TYPE_MOBILE
    }

    private fun emailTypes(type: Int): Array<EmailType> = when (type) {
        Email.TYPE_HOME -> arrayOf(EmailType.HOME)
        Email.TYPE_WORK -> arrayOf(EmailType.WORK)
        else -> arrayOf(EmailType.INTERNET)
    }

    private fun emailType(types: List<EmailType>): Int = when {
        EmailType.WORK in types -> Email.TYPE_WORK
        EmailType.HOME in types -> Email.TYPE_HOME
        else -> Email.TYPE_OTHER
    }

    private fun addressTypes(type: Int): AddressType? = when (type) {
        StructuredPostal.TYPE_HOME -> AddressType.HOME
        StructuredPostal.TYPE_WORK -> AddressType.WORK
        else -> null
    }

    private fun addressType(types: List<AddressType>): Int = when {
        AddressType.WORK in types -> StructuredPostal.TYPE_WORK
        AddressType.HOME in types -> StructuredPostal.TYPE_HOME
        else -> StructuredPostal.TYPE_OTHER
    }

    private fun birthday(text: String): Birthday? {
        if (text.isBlank()) return null
        if (FULL_DATE.matches(text)) return runCatching { Birthday(LocalDate.parse(text)) }.getOrNull()
        return Birthday(text)
    }

    private fun birthdayText(birthday: Birthday): String {
        birthday.text?.let { return it }
        birthday.partialDate?.let { partial ->
            val year = partial.year
            val month = partial.month ?: return ""
            val day = partial.date ?: return ""
            return if (year != null) "%04d-%02d-%02d".format(year, month, day) else "--%02d-%02d".format(month, day)
        }
        val date = birthday.date ?: return ""
        return when (date) {
            is LocalDate -> date.toString()
            else -> runCatching { LocalDate.from(date).toString() }.getOrDefault("")
        }
    }
}
