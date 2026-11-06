package io.github.andreygladkov.inkcalculator.calc

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntryTest {
    private fun typed(keys: String, from: Entry = Entry()) = keys.fold(from) { entry, key ->
        when (key) {
            in '0'..'9' -> entry.digits(key.toString())
            'D' -> entry.digits("00")
            '.' -> entry.point()
            '%' -> entry.percent()
            '<' -> entry.erase()
            '=' -> (entry.evaluate() as Outcome.Value).let { Entry.result(it.number) }
            else -> entry.operator(key)
        }
    }

    private fun value(entry: Entry) = Arithmetic.shown((entry.evaluate() as Outcome.Value).number)

    @Test fun leadingZerosAreReplacedAndADoubleZeroTypesBothDigits() {
        assertEquals("5", typed("005").text)
        assertEquals("0", typed("D0").text)
        assertEquals("500", typed("5D").text)
        assertEquals("-5", typed("-05").text)
        assertEquals("0.05", typed(".05").text)
    }

    @Test fun aNumberHoldsAtMostFifteenSignificantDigits() {
        assertEquals("0.000123456789012345", typed(".000123456789012345").text)
        assertEquals("123456789012345", typed("1234567890123456").text)
        assertEquals("12345678901234", typed("12345678901234D").text)
        assertEquals("123456789012345+6", typed("1234567890123456+6").text)
    }

    @Test fun aNumberTakesOnePointAndAPointStartsWithZero() {
        assertEquals("1.5", typed("1.5.").text)
        assertEquals("2+0.", typed("2+.").text)
        assertEquals("-0.", typed("-.").text)
    }

    @Test fun anOperatorReplacesTheLastOneExceptMinusAfterTimesOrDivide() {
        assertEquals("2*", typed("2+-*").text)
        assertEquals("2*-3", typed("2*-3").text)
        assertEquals("2/-", typed("2/--").text)
        assertEquals("2+", typed("2*-+").text)
        assertEquals("", typed("*").text)
        assertEquals("-", typed("-").text)
        assertEquals("", typed("-+").text)
    }

    @Test fun aPercentFollowsANumberOnceAndEndsIt() {
        assertEquals("10%", typed("10%%5.").text)
        assertEquals("", typed("%").text)
        assertEquals("-", typed("-%").text)
        assertEquals("10%+", typed("10%+").text)
    }

    @Test fun eraseTakesTheLastKey() {
        assertEquals("12+", typed("12+3<").text)
        assertEquals("", typed("1<<").text)
    }

    @Test fun afterAResultADigitStartsAnewAndAnOperatorCarriesItOn() {
        val result = typed("2+3=")
        assertTrue(result.isResult)
        assertEquals("5", result.text)
        assertEquals("7", typed("7", result).text)
        assertEquals("0.", typed(".", result).text)
        assertEquals("5*", typed("*", result).text)
        assertEquals("5%", typed("%", result).text)
        assertEquals("", typed("<", result).text)
    }

    @Test fun aRoundedResultCarriesOnExactly() {
        val third = typed("1/3=")
        assertEquals("0.333333333333333", third.text)
        assertEquals("1", value(typed("*3", third)))
        assertEquals("1", typed("*3=", third).text)
    }

    @Test fun editingTheCarriedNumberDropsItsExactValue() {
        val third = typed("1/3=")
        val retyped = typed("+<<3", third)
        assertEquals("0.333333333333333", retyped.text)
        assertEquals("0.999999999999999", value(typed("*3", retyped)))
    }

    @Test fun aHugeResultIsErasedWhole() {
        val huge = Entry.result(BigDecimal("1.5E20"))
        assertEquals("1.5E20", huge.text)
        assertEquals("1.5E20+", typed("+", huge).text)
        assertEquals("", typed("+<<", huge).text)
        assertEquals("1.5E20", typed("+<5", huge).text)
        assertEquals("1.5E20", typed("+<%<", huge).text)
    }

    @Test fun anInsertedNumberReplacesTheTypedOneOrFollowsAnOperator() {
        assertEquals("42", Entry().insert("42").text)
        assertEquals("2+42", typed("2+").insert("42").text)
        assertEquals("2+42", typed("2+3%").insert("42").text)
        assertEquals("2*-42", typed("2*-").insert("42").text)
        assertEquals("2*3", typed("2*-").insert("-3").text)
        assertTrue(typed("2+3=").insert("42").isResult)
    }

    @Test fun theTextStopsGrowingAtItsLimit() {
        val long = typed("1+".repeat(Entry.MAX_LENGTH))
        assertEquals(Entry.MAX_LENGTH, long.text.length)
        assertEquals(long, typed("2", long))
    }

    @Test fun onlyAnExpressionWithAnOperatorOrPercentIsACalculation() {
        assertFalse(typed("12").isCalculation)
        assertFalse(typed("12+").isCalculation)
        assertTrue(typed("12+3").isCalculation)
        assertTrue(typed("12%").isCalculation)
        assertFalse(typed("-").isCalculation)
    }
}
