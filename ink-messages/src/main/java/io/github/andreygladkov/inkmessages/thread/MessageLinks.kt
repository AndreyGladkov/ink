package io.github.andreygladkov.inkmessages.thread

import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify

object MessageLinks {
    // Linkify accepts 7-digit local numbers, which bank amounts like "15000.00" or "1 500 000" also are;
    // a full number in the +7 zone (and most others) has at least 10 digits.
    private const val MIN_PHONE_DIGITS = 10

    fun of(body: CharSequence): CharSequence {
        val text = SpannableString(body)
        if (!Linkify.addLinks(text, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS)) return body
        val spans = text.getSpans(0, text.length, URLSpan::class.java)
        spans.filter(::isShortPhone).forEach(text::removeSpan)
        return if (spans.all(::isShortPhone)) body else text
    }

    private fun isShortPhone(span: URLSpan): Boolean =
        span.url.startsWith("tel:") && span.url.count(Char::isDigit) < MIN_PHONE_DIGITS
}
