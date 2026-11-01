package io.github.andreygladkov.inkphone.vcard

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VcardMapperTest {
    private val full = CardData(
        formattedName = "Иван Петрович Сидоров",
        given = "Иван", middle = "Петрович", family = "Сидоров",
        nickname = "Ваня",
        phones = listOf(TypedValue("+7 701 111 22 33", Phone.TYPE_MOBILE), TypedValue("+7 727 222 33 44", Phone.TYPE_WORK), TypedValue("+7 727 000 00 00", Phone.TYPE_FAX_WORK)),
        emails = listOf(TypedValue("ivan@example.com", Email.TYPE_HOME), TypedValue("i.sidorov@work.example", Email.TYPE_WORK)),
        addresses = listOf(PostalAddress(street = "ул. Абая, 1", city = "Алматы", postcode = "050000", country = "Казахстан", type = StructuredPostal.TYPE_HOME)),
        organization = "ТОО «Рога»", title = "Директор",
        birthday = "1980-05-17",
        note = "Звонить после 18:00",
        urls = listOf("https://example.com"),
    )

    private fun roundTrip(cards: List<CardData>): List<CardData> {
        val out = ByteArrayOutputStream()
        VcardMapper.write(cards, out)
        return VcardMapper.read(out.toByteArray().inputStream())
    }

    @Test fun everyFieldSurvivesARoundTrip() {
        assertEquals(listOf(full), roundTrip(listOf(full)))
    }

    @Test fun writesVcard30InUtf8() {
        val out = ByteArrayOutputStream()
        VcardMapper.write(listOf(full), out)
        val text = out.toString(Charsets.UTF_8)
        assertTrue("VERSION:3.0" in text)
        assertTrue("N:Сидоров;Иван;Петрович\r\n" in text, text)
        assertTrue("TEL;TYPE=cell:+7 701 111 22 33" in text, text)
    }

    @Test fun yearlessBirthdayAndNameOnlyCards() {
        val cards = listOf(CardData(given = "Анна", birthday = "--03-08"), CardData(formattedName = "Такси", phones = listOf(TypedValue("8800", Phone.TYPE_MOBILE))))
        val read = roundTrip(cards)
        assertEquals("--03-08", read[0].birthday)
        assertEquals("Анна", read[0].displayName)
        assertEquals("Такси", read[1].displayName)
    }

    @Test fun readsGoogleExportAndOldNokiaQuotedPrintable() {
        val google = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Мама
            N:;Мама;;;
            TEL;TYPE=CELL:+7 927 505-75-04
            EMAIL;TYPE=INTERNET:mama@example.com
            END:VCARD
            BEGIN:VCARD
            VERSION:2.1
            N;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:=D0=9F=D0=B5=D1=82=D1=80=D0=BE=D0=B2;=D0=9F=D1=91=D1=82=D1=80;;;
            TEL;HOME:8 727 111 11 11
            TEL:8 777 222 22 22
            END:VCARD
        """.trimIndent()
        val cards = VcardMapper.read(google.byteInputStream())
        assertEquals("Мама", cards[0].displayName)
        assertEquals(listOf(TypedValue("+7 927 505-75-04", Phone.TYPE_MOBILE)), cards[0].phones)
        assertEquals(listOf(TypedValue("mama@example.com", Email.TYPE_OTHER)), cards[0].emails)
        assertEquals("Петров", cards[1].family)
        assertEquals("Пётр", cards[1].given)
        assertEquals(listOf(Phone.TYPE_HOME, Phone.TYPE_MOBILE), cards[1].phones.map { it.type })
    }

    @Test fun displayNameFallsBackToNumber() {
        assertEquals("+7 700", CardData(phones = listOf(TypedValue("+7 700", Phone.TYPE_MOBILE))).displayName)
    }
}
