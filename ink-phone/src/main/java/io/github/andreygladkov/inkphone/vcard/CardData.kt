package io.github.andreygladkov.inkphone.vcard

data class TypedValue(val value: String, val type: Int, val label: String? = null)

data class PostalAddress(
    val street: String = "",
    val city: String = "",
    val region: String = "",
    val postcode: String = "",
    val country: String = "",
    val type: Int,
)

data class CardData(
    val formattedName: String = "",
    val prefix: String = "",
    val given: String = "",
    val middle: String = "",
    val family: String = "",
    val suffix: String = "",
    val nickname: String = "",
    val phones: List<TypedValue> = emptyList(),
    val emails: List<TypedValue> = emptyList(),
    val addresses: List<PostalAddress> = emptyList(),
    val organization: String = "",
    val title: String = "",
    val birthday: String = "",
    val note: String = "",
    val urls: List<String> = emptyList(),
) {
    val displayName: String
        get() = formattedName.ifBlank { listOf(prefix, given, middle, family, suffix).filter { it.isNotBlank() }.joinToString(" ") }
            .ifBlank { organization }
            .ifBlank { phones.firstOrNull()?.value.orEmpty() }
            .ifBlank { emails.firstOrNull()?.value.orEmpty() }
}
