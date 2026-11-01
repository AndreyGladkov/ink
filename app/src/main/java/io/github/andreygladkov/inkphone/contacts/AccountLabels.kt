package io.github.andreygladkov.inkphone.contacts

import android.accounts.AccountManager
import android.content.Context
import io.github.andreygladkov.inkphone.R

class AccountLabels(private val context: Context) {
    private val authenticatorLabels: Map<String, String> by lazy {
        AccountManager.get(context).authenticatorTypes.mapNotNull { description ->
            val label = runCatching {
                context.packageManager.getResourcesForApplication(description.packageName).getString(description.labelId)
            }.getOrNull()
            label?.let { description.type to it }
        }.toMap()
    }

    fun label(account: AccountRef): String {
        val type = account.type ?: return context.getString(R.string.account_local)
        val label = authenticatorLabels[type] ?: if (type == ContactWriter.GOOGLE) "Google" else null
        if (label == null) return account.name ?: type
        return listOfNotNull(label, account.name?.takeIf { it != label }).joinToString(" · ")
    }
}
