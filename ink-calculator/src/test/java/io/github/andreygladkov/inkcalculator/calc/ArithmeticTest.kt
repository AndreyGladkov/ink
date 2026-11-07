package io.github.andreygladkov.inkcalculator.calc

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class ArithmeticTest {
    private fun calc(text: String): String = when (val outcome = Arithmetic.evaluate(tokenize(text)!!)) {
        is Outcome.Value -> Arithmetic.shown(outcome.number)
        else -> outcome.toString()
    }

    @Test fun multiplicationBindsBeforeAddition() {
        assertEquals("14", calc("2+3*4"))
        assertEquals("-10", calc("2-3*4"))
        assertEquals("2.5", calc("10/2/2"))
        assertEquals("-6", calc("-2*3"))
        assertEquals("-6", calc("2*-3"))
    }

    @Test fun decimalsAddExactly() {
        assertEquals("0.3", calc("0.1+0.2"))
        assertEquals("1", calc("1.1-0.1"))
        assertEquals("5", calc("5."))
    }

    @Test fun aPercentOfASumIsAddedOrSubtracted() {
        assertEquals("220", calc("200+10%"))
        assertEquals("180", calc("200-10%"))
        assertEquals("110", calc("2*50+10%"))
    }

    @Test fun aPercentElsewhereIsAHundredth() {
        assertEquals("0.5", calc("50%"))
        assertEquals("20", calc("200*10%"))
        assertEquals("2000", calc("200/10%"))
        assertEquals("200.3", calc("200+3*10%"))
        assertEquals("200.3", calc("200+10%*3"))
    }

    @Test fun roundingErrorDoesNotShowUpAsANumber() {
        assertEquals("0", calc("1/3*3-1"))
        assertEquals("1", calc("100000000000000000000+1-100000000000000000000"))
        assertEquals("1", calc("100000000000000*100000000000000*1000+1-100000000000000*100000000000000*1000"))
    }

    @Test fun divisionByZeroAndHugeNumbersAreErrors() {
        assertEquals("DivisionByZero", calc("5/0"))
        assertEquals("DivisionByZero", calc("5/0%"))
        assertEquals("TooLarge", calc("999999999999999*999999999999999*999999999999999*999999999999999*999999999999999*999999999999999*999999999999999"))
    }

    @Test fun anUnfinishedExpressionIsInvalid() {
        assertEquals("Invalid", calc("2+"))
        assertEquals("Invalid", calc("-"))
        assertEquals("Invalid", Arithmetic.evaluate(emptyList()).toString())
    }

    @Test fun resultsAreRoundedToFifteenDigitsWithAnExponentOnlyFarFromOne() {
        assertEquals("0.333333333333333", calc("1/3"))
        assertEquals("0.666666666666667", calc("2/3"))
        assertEquals("123.45", Arithmetic.shown(BigDecimal("123.4500")))
        assertEquals("0.00001", Arithmetic.shown(BigDecimal("0.00001")))
        assertEquals("1E-6", Arithmetic.shown(BigDecimal("0.000001")))
        assertEquals("999999999999999", Arithmetic.shown(BigDecimal("999999999999999")))
        assertEquals("1E15", Arithmetic.shown(BigDecimal("999999999999999.9")))
        assertEquals("-1.23456789012346E19", Arithmetic.shown(BigDecimal("-12345678901234567890")))
    }

    @Test fun anExponentIsReadBack() {
        assertEquals("3E20", calc("1.5E20*2"))
        assertEquals("1.5E-12", calc("1.5E-12"))
    }
}
