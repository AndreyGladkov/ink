package io.github.andreygladkov.inkmessages.data

import android.app.role.RoleManager
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.BlockedNumberContract.BlockedNumbers

/** The system block list: like the real one, only the default SMS app may read or change it. */
class FakeBlockedNumberProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method == "can_current_user_block_numbers") return Bundle().apply { putBoolean("can_block", isDefaultApp()) }
        requireDefaultApp()
        return when (method) {
            "is_blocked" -> Bundle().apply { putBoolean("blocked", numbers.any { same(it, arg.orEmpty()) }) }
            "unblock" -> Bundle().apply { putInt("num_deleted", if (numbers.removeAll { same(it, arg.orEmpty()) }) 1 else 0) }
            else -> error("unexpected method $method")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        requireDefaultApp()
        numbers += values!!.getAsString(BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
        return ContentUris.withAppendedId(BlockedNumbers.CONTENT_URI, numbers.size.toLong())
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        requireDefaultApp()
        return MatrixCursor(arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER)).apply { numbers.forEach { addRow(arrayOf(it)) } }
    }

    override fun getType(uri: Uri): String? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    private fun isDefaultApp() = context!!.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)

    private fun requireDefaultApp() {
        if (!isDefaultApp()) throw SecurityException("caller must be the default SMS or dialer app")
    }

    private fun same(a: String, b: String): Boolean {
        val x = a.filter(Char::isDigit).takeLast(10)
        return if (x.isEmpty()) a.equals(b, ignoreCase = true) else x == b.filter(Char::isDigit).takeLast(10)
    }

    companion object {
        val numbers = ArrayList<String>()

        fun reset() = numbers.clear()
    }
}
