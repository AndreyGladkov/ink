package io.github.andreygladkov.inkclock.ui

/**
 * Digits typed on a keypad shift in from the right, so "730" reads as 07:30 and a fourth digit is optional. Zeros typed
 * first change nothing on screen, so they take no place; a key that does not fit whole is ignored.
 */
data class DigitEntry(val digits: String, val maxDigits: Int) {
    fun type(key: String): DigitEntry {
        val typed = if (digits.isEmpty()) key.trimStart('0') else digits + key
        return if (typed.length <= maxDigits) copy(digits = typed) else this
    }

    fun erase(): DigitEntry = copy(digits = digits.dropLast(1))

    fun groups(): List<Int> = digits.padStart(maxDigits, '0').chunked(2).map(String::toInt)

    companion object {
        fun of(maxDigits: Int, vararg groups: Int) = DigitEntry(groups.joinToString("") { "%02d".format(it) }, maxDigits)
    }
}
