package io.github.andreygladkov.inkmessages.sms

object OtpCode {
    private val KEYWORD = Regex("(?i)(код|code|пароль|password|parol|kod|pin)")
    private val CODE = Regex("(?<![\\d.,:/-])\\d{4,8}(?![\\d.,:/-]?\\d)")
    private const val MAX_DISTANCE = 40

    fun find(text: String): String? {
        val keywords = KEYWORD.findAll(text).map { it.range }.toList()
        if (keywords.isEmpty()) return null
        return CODE.findAll(text)
            .filter { code -> keywords.any { keyword -> distance(code.range, keyword) <= MAX_DISTANCE } }
            .minByOrNull { code -> keywords.minOf { distance(code.range, it) } }
            ?.value
    }

    private fun distance(a: IntRange, b: IntRange): Int = when {
        a.last < b.first -> b.first - a.last
        b.last < a.first -> a.first - b.last
        else -> 0
    }
}
