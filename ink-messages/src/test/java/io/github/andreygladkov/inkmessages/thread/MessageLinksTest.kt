package io.github.andreygladkov.inkmessages.thread

import android.telephony.TelephonyManager
import android.text.Spanned
import android.text.style.URLSpan
import io.github.andreygladkov.inkmessages.TestData.context
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
class MessageLinksTest {
    @Before fun kazakhSim() {
        shadowOf(context.getSystemService(TelephonyManager::class.java)).setSimCountryIso("kz")
    }

    private fun links(text: String): List<String> {
        val linked = MessageLinks.of(text) as? Spanned ?: return emptyList()
        return linked.getSpans(0, linked.length, URLSpan::class.java).sortedBy(linked::getSpanStart).map { it.url }
    }

    @Test fun webAddressesAndEmailsBecomeLinks() {
        assertEquals(listOf("https://hh.kz/vacancy/1", "mailto:hr@hh.kz"), links("Вакансия https://hh.kz/vacancy/1, вопросы на hr@hh.kz"))
    }

    @Test fun fullPhoneNumbersBecomeLinks() {
        assertEquals(listOf("tel:+79001112233"), links("Позвоните +7 900 111-22-33"))
        assertEquals(listOf("tel:88005553535"), links("Горячая линия 8 800 555-35-35"))
        assertEquals(listOf("tel:87011234567", "tel:87273445566"), links("Звоните 87011234567 или 8 (727) 344-55-66"))
    }

    @Test fun amountsAndCodesStayPlainText() {
        assertEquals(emptyList(), links("Списание 15000.00 KZT, баланс 1234567.89"))
        assertEquals(emptyList(), links("Перевод 1 500 000 тг выполнен"))
        assertEquals(emptyList(), links("Код подтверждения: 482913"))
        assertEquals(emptyList(), links("Номер счёта 40817810099910004312"))
    }

    @Test fun textWithoutLinksIsReturnedAsIs() {
        listOf("Привет", "Списание 15000.00 KZT").forEach { text -> assertSame(text, MessageLinks.of(text)) }
    }
}
