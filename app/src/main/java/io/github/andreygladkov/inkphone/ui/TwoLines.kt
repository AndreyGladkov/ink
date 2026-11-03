package io.github.andreygladkov.inkphone.ui

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import io.github.andreygladkov.inkphone.eink.InkStyle


fun twoLines(title: CharSequence, subtitle: CharSequence): CharSequence {
    val text = SpannableStringBuilder(title).append('\n')
    val start = text.length
    text.append(subtitle)
    text.setSpan(RelativeSizeSpan(InkStyle.SECONDARY_SCALE), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return text
}
