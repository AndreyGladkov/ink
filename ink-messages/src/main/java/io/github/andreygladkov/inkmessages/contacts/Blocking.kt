package io.github.andreygladkov.inkmessages.contacts

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers

/**
 * The system block list, shared with the dialer. Only the default SMS or phone app may use it, so every call
 * fails quietly for a read-only Ink Messages. The system itself drops calls and messages from blocked numbers;
 * this app only hides their old conversations.
 */
class Blocking(private val context: Context) {
    private val known = HashMap<String, Boolean>()

    fun isAvailable(): Boolean = runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false)

    fun isBlocked(address: String): Boolean = known.getOrPut(address) {
        runCatching { BlockedNumberContract.isBlocked(context, address) }.getOrDefault(false)
    }

    fun block(address: String): Boolean {
        known.remove(address)
        val values = ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, address) }
        return runCatching { context.contentResolver.insert(BlockedNumbers.CONTENT_URI, values) != null }.getOrDefault(false)
    }

    fun unblock(address: String): Boolean {
        known.remove(address)
        return runCatching { BlockedNumberContract.unblock(context, address) > 0 }.getOrDefault(false)
    }
}
