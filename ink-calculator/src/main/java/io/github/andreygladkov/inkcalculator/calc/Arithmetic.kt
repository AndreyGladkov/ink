package io.github.andreygladkov.inkcalculator.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

sealed interface Outcome {
    data class Value(val number: BigDecimal) : Outcome
    data object DivisionByZero : Outcome
    data object TooLarge : Outcome
    data object Invalid : Outcome
}

object Arithmetic {
    const val SHOWN_DIGITS = 15
    private val CONTEXT = MathContext.DECIMAL128
    private val SHOWN = MathContext(SHOWN_DIGITS, RoundingMode.HALF_UP)
    private val PLAIN_EXPONENTS = -5 until SHOWN_DIGITS
    private val HUNDRED = BigDecimal(100)
    private val LIMIT = BigDecimal.ONE.scaleByPowerOfTen(100)
    private val NEGLIGIBLE = BigDecimal.ONE.scaleByPowerOfTen(-100)

    /**
     * Multiplication and division bind before addition and subtraction. A percent added to or subtracted from
     * a sum is a percent of that sum, so 200 + 10% is 220, as on desk calculators; anywhere else it is a
     * hundredth. [first] replaces the value of the first number, for a rounded result carried on exactly.
     */
    fun evaluate(tokens: List<Token>, first: BigDecimal? = null): Outcome {
        if (tokens.isEmpty() || tokens.last() !is Num) return Outcome.Invalid
        val numbers = tokens.filterIsInstance<Num>()
        val ops = tokens.filterIsInstance<Op>().map { it.symbol }
        var total = BigDecimal.ZERO
        var sign = '+'
        var term = BigDecimal.ZERO
        for ((i, number) in numbers.withIndex()) {
            val value = (if (i == 0) first else null) ?: number.text.toBigDecimalOrNull() ?: return Outcome.Invalid
            val op = ops.getOrNull(i - 1)
            when (op) {
                null, '+', '-' -> {
                    if (op != null) total = add(total, sign, term)
                    sign = op ?: '+'
                    val isWholeTerm = ops.getOrNull(i).let { it == null || it == '+' || it == '-' }
                    term = when {
                        !number.isPercent -> value
                        op != null && isWholeTerm -> total.multiply(value, CONTEXT).divide(HUNDRED, CONTEXT)
                        else -> value.divide(HUNDRED, CONTEXT)
                    }
                }
                '*' -> term = term.multiply(hundredth(value, number), CONTEXT)
                '/' -> {
                    val divisor = hundredth(value, number)
                    if (divisor.signum() == 0) return Outcome.DivisionByZero
                    term = term.divide(divisor, CONTEXT)
                }
            }
            if (term.abs() >= LIMIT) return Outcome.TooLarge
        }
        val result = add(total, sign, term)
        return when {
            result.abs() >= LIMIT -> Outcome.TooLarge
            result.abs() < NEGLIGIBLE -> Outcome.Value(BigDecimal.ZERO)
            else -> Outcome.Value(result)
        }
    }

    /** The value as shown and typed on: rounded to the digits that fit, with an exponent only when it is far from 1. */
    fun shown(value: BigDecimal): String {
        val rounded = value.round(SHOWN).stripTrailingZeros()
        if (rounded.signum() == 0) return "0"
        val exponent = rounded.precision() - rounded.scale() - 1
        return if (exponent in PLAIN_EXPONENTS) rounded.toPlainString()
        else "${rounded.movePointLeft(exponent).toPlainString()}$EXPONENT$exponent"
    }

    private fun hundredth(value: BigDecimal, number: Num) = if (number.isPercent) value.divide(HUNDRED, CONTEXT) else value

    // 1 ÷ 3 × 3 − 1 leaves 1e-34 of rounding error, which would show as a number instead of 0. A sum more digits
    // below its operands than the working precision holds can only be such an error; an exact one is never that small.
    private fun add(total: BigDecimal, sign: Char, term: BigDecimal): BigDecimal {
        val sum = if (sign == '-') total.subtract(term, CONTEXT) else total.add(term, CONTEXT)
        val largest = total.abs().max(term.abs())
        return if (sum.signum() != 0 && sum.abs().scaleByPowerOfTen(CONTEXT.precision - 1) < largest) BigDecimal.ZERO else sum
    }
}
