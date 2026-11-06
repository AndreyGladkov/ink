package io.github.andreygladkov.inkcalculator.calc

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayFormatTest {
    private val russian = DisplayFormat.of(Locale.forLanguageTag("ru"))
    private val english = DisplayFormat.of(Locale.ENGLISH)

    @Test fun numbersAreGroupedWithTheLocaleSeparators() {
        assertEquals("1 234 567,5 × −3", russian.expression("1234567.5*-3"))
        assertEquals("1,234,567.50 ÷ 3", english.expression("1234567.50/3"))
        assertEquals("−0,", russian.expression("-0."))
    }

    @Test fun operatorsAndPercentsAreShownAsSigns() {
        assertEquals("200 + 10% − 5 × 2 ÷ 1", english.expression("200+10%-5*2/1"))
        assertEquals("12 + ", english.expression("12+"))
    }

    @Test fun anExponentIsShownAfterE() {
        assertEquals("1,5e−12", russian.expression("1.5E-12"))
        assertEquals("−1.23e20", english.expression("-1.23E20"))
    }

    @Test fun aCopiedNumberIsUngroupedWithAPlainMinusAndExponent() {
        assertEquals("-1234,5", russian.copyable("-1234.5"))
        assertEquals("1.5E-12", english.copyable("1.5E-12"))
    }
}
