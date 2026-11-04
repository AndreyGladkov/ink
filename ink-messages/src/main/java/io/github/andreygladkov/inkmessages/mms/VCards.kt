package io.github.andreygladkov.inkmessages.mms

import java.io.ByteArrayOutputStream

object VCards {
    private val FORMATTED_NAME = Regex("""^FN((?:;[^:\r\n]*)?):(.*)$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

    /** The FN property of the first card, which vCard 2.1 senders often write as UTF-8 quoted-printable. */
    fun displayName(text: String): String? {
        val unfolded = text.replace(Regex("""\r?\n[ \t]"""), "").replace(Regex("""=\r?\n"""), "")
        val match = FORMATTED_NAME.find(unfolded) ?: return null
        val (params, value) = match.destructured
        val name = if (params.contains("QUOTED-PRINTABLE", ignoreCase = true)) quotedPrintable(value) else value
        return name.trim().takeIf(String::isNotEmpty)
    }

    private fun quotedPrintable(value: String): String {
        val bytes = ByteArrayOutputStream()
        var index = 0
        while (index < value.length) {
            val hex = if (value[index] == '=' && index + 2 < value.length) value.substring(index + 1, index + 3).toIntOrNull(16) else null
            if (hex != null) {
                bytes.write(hex)
                index += 3
            } else {
                bytes.write(value[index].code)
                index++
            }
        }
        return bytes.toString(Charsets.UTF_8.name())
    }
}
