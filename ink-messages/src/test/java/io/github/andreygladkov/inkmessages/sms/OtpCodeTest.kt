package io.github.andreygladkov.inkmessages.sms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OtpCodeTest {
    @Test fun findsCodesNextToKeywords() {
        assertEquals("70427", OtpCode.find("Никому не сообщайте код: 70427. СПИСАНИЕ 1000.00 RUB"))
        assertEquals("538945", OtpCode.find("Сайт talentpulse: 538945 - ваш код"))
        assertEquals("8043", OtpCode.find("Никому не говорите код 8043. Вход в Т-Банк в 12:48 01.11.26"))
        assertEquals("123456", OtpCode.find("Your verification code is 123456"))
    }

    @Test fun ignoresNumbersWithoutAKeywordAndDatesOrAmounts() {
        assertNull(OtpCode.find("Этот абонент появился в сети 01.11 10:01"))
        assertNull(OtpCode.find("Ваше парковочное разрешение 0353290-2025 заканчивается"))
        assertNull(OtpCode.find("Код операции: списание 1000.00 RUB"))
    }
}
