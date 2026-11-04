package io.github.andreygladkov.inkmessages.contacts

import android.net.Uri

data class Participant(
    val address: String,
    val name: String? = null,
    val isContact: Boolean = false,
    val isStarred: Boolean = false,
    /** A lookup URI, which keeps pointing at the contact after the contacts provider merges or splits it. */
    val contactUri: Uri? = null,
)
