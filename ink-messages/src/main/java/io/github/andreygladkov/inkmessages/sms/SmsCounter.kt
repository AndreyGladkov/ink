package io.github.andreygladkov.inkmessages.sms

object SmsCounter {
    const val WARN_REMAINING = 10

    /** Shown only near a part boundary, so short messages stay free of numbers. */
    fun isWorthShowing(text: CharSequence, parts: Int, remaining: Int): Boolean =
        text.isNotEmpty() && (parts > 1 || remaining < WARN_REMAINING)
}
