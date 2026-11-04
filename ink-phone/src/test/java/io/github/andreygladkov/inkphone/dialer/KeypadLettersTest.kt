package io.github.andreygladkov.inkphone.dialer

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeypadLettersTest {
    private val english = listOf(Locale.US)

    @Test fun autoShowsCyrillicWhenRussianIsAnySystemLanguage() {
        assertTrue(KeypadLetters.AUTO.showsCyrillic(listOf(Locale.US, Locale.forLanguageTag("ru-RU")), listOf("Anna")))
    }

    @Test fun autoIgnoresOtherCyrillicSystemLanguages() {
        assertFalse(KeypadLetters.AUTO.showsCyrillic(listOf(Locale.forLanguageTag("uk-UA"), Locale.forLanguageTag("bg")), listOf("Anna")))
    }

    @Test fun autoShowsCyrillicForCyrillicContactOnEnglishSystem() {
        assertTrue(KeypadLetters.AUTO.showsCyrillic(english, listOf("Anna Smith", "Мама")))
    }

    @Test fun autoHidesCyrillicForLatinOnlyContactsOnEnglishSystem() {
        assertFalse(KeypadLetters.AUTO.showsCyrillic(english, listOf("Anna Smith", "José")))
        assertFalse(KeypadLetters.AUTO.showsCyrillic(english, emptyList()))
    }

    @Test fun explicitChoiceOverridesSystemAndContacts() {
        assertFalse(KeypadLetters.LATIN.showsCyrillic(listOf(Locale.forLanguageTag("ru")), listOf("Мама")))
        assertTrue(KeypadLetters.LATIN_CYRILLIC.showsCyrillic(english, listOf("Anna")))
    }
}
