package io.github.andreygladkov.inkclock.ui

/** Digits typed on a keypad shift in from the right, so "730" reads as 07:30 and a fourth digit is optional. */
data class DigitEntry(val digits: String, val maxDigits: Int) {
    fun type(digit: Char): DigitEntry = if (digits.length < maxDigits) copy(digits = digits + digit) else this

    fun erase(): DigitEntry = copy(digits = digits.dropLast(1))

    fun groups(): List<Int> = digits.padStart(maxDigits, '0').chunked(2).map(String::toInt)

    companion object {
        fun of(maxDigits: Int, vararg groups: Int) = DigitEntry(groups.joinToString("") { "%02d".format(it) }, maxDigits)
    }
}
