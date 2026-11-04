package io.github.andreygladkov.inkphone.contacts

data class AccountRef(val type: String?, val name: String?) {
    fun encode(): String = "${type.orEmpty()}$SEPARATOR${name.orEmpty()}"

    companion object {
        private const val SEPARATOR = '\u001F'

        fun decode(value: String): AccountRef {
            val type = value.substringBefore(SEPARATOR)
            val name = value.substringAfter(SEPARATOR, "")
            return AccountRef(type.ifEmpty { null }, name.ifEmpty { null })
        }
    }
}

data class ContactSummary(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val nameAlternative: String,
    val starred: Boolean,
    val hasPhone: Boolean,
    val numbers: List<String>,
    val accounts: Set<AccountRef>,
) {
    val numberDigits: List<String> = numbers.map { number -> number.filter(Char::isDigit) }
}

data class PhoneEntry(val dataId: Long, val number: String, val type: Int, val label: String?, val isDefault: Boolean)

data class ContactDetails(
    val id: Long,
    val name: String,
    val phones: List<PhoneEntry>,
    val emails: List<String>,
    val accounts: List<AccountRef>,
    val starred: Boolean = false,
)
