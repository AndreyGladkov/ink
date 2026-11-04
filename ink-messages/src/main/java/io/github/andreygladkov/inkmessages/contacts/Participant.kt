package io.github.andreygladkov.inkmessages.contacts

data class Participant(
    val address: String,
    val name: String? = null,
    val isContact: Boolean = false,
    val isStarred: Boolean = false,
)
