package io.github.andreygladkov.inkphone.calllog

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.ui.IconSpan
import java.time.ZoneId

class CallLabels(private val context: Context) {
    private val time = CallTimeFormat(context.getString(R.string.yesterday), ZoneId.systemDefault())

    fun type(type: CallType): String = context.getString(
        when (type) {
            CallType.INCOMING -> R.string.call_type_incoming
            CallType.OUTGOING -> R.string.call_type_outgoing
            CallType.MISSED -> R.string.call_type_missed
            CallType.REJECTED -> R.string.call_type_rejected
            CallType.BLOCKED -> R.string.call_type_blocked
            CallType.VOICEMAIL, CallType.OTHER -> R.string.call_type_voicemail
        },
    )

    fun summary(entry: CallLogEntry, nowMillis: Long = System.currentTimeMillis()): CharSequence {
        val text = SpannableStringBuilder(type(entry.type))
        text.setSpan(IconSpan(context, icon(entry.type)), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text.append("  ").append(time.format(entry.dateMillis, nowMillis))
    }

    private fun icon(type: CallType): Int = when (type) {
        CallType.INCOMING -> R.drawable.ic_call_received
        CallType.OUTGOING -> R.drawable.ic_call_made
        CallType.MISSED -> R.drawable.ic_call_missed
        CallType.REJECTED -> R.drawable.ic_call_end
        CallType.BLOCKED -> R.drawable.ic_block
        CallType.VOICEMAIL, CallType.OTHER -> R.drawable.ic_voicemail
    }

    fun title(entry: CallLogEntry): String = entry.name ?: entry.number.ifEmpty { context.getString(R.string.call_unknown) }
}
