package io.github.andreygladkov.inkmessages.data

import android.Manifest
import android.app.role.RoleManager
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony.TextBasedSmsColumns

class FakeTelephonyProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sortOrder: String?): Cursor {
        requirePermission(Manifest.permission.READ_SMS)
        if (uri.authority == "mms-sms" && uri.path == "/threadID") {
            requireDefaultApp()
            val id = threadFor(uri.getQueryParameters("recipient"))
            return MatrixCursor(arrayOf("_id")).apply { addRow(arrayOf(id)) }
        }
        val (table, id) = tableOf(uri)
        val rows = table.filter { row -> (id == null || row["_id"] == id) && matches(row, selection) }
        return MatrixCursor(projection).apply { rows.forEach { row -> addRow(projection!!.map { row[it] }) } }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        requirePermission(Manifest.permission.READ_SMS)
        if (!isDefaultApp()) return null
        require(uri.authority == "sms") { "only SMS inserts are faked: $uri" }
        val row = values!!.keySet().associateWith { values.get(it) }.toMutableMap()
        row["type"] = when (uri.path) {
            "/inbox" -> TextBasedSmsColumns.MESSAGE_TYPE_INBOX
            "/outbox" -> TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX
            else -> row["type"]
        }
        val id = sms.size + 1L
        row["_id"] = id
        row["thread_id"] = threadFor(listOf(row["address"] as String))
        row.putIfAbsent("sub_id", -1)
        row.putIfAbsent("read", 0)
        sms += row
        context!!.contentResolver.notifyChange(uri, null)
        return ContentUris.withAppendedId(Uri.parse("content://sms"), id)
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        requirePermission(Manifest.permission.READ_SMS)
        if (!isDefaultApp()) return 0
        val (table, id) = tableOf(uri)
        val rows = table.filter { row -> (id == null || row["_id"] == id) && matches(row, selection) }
        rows.forEach { row -> values!!.keySet().forEach { key -> row[key] = values.get(key) } }
        if (rows.isNotEmpty()) context!!.contentResolver.notifyChange(uri, null)
        return rows.size
    }

    override fun getType(uri: Uri): String? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    private fun tableOf(uri: Uri): Pair<MutableList<MutableMap<String, Any?>>, Long?> {
        val segments = uri.pathSegments
        val id = segments.lastOrNull()?.toLongOrNull()
        return when (uri.authority) {
            "mms-sms" -> when (segments.firstOrNull()) {
                "conversations" -> {
                    require(uri.getQueryParameter("simple") == "true") { "only simple thread queries are faked" }
                    threads to null
                }
                "canonical-addresses" -> addresses to null
                else -> error("unexpected uri $uri")
            }
            "sms" -> sms to id
            "mms" -> if (segments.firstOrNull() == "part") parts to null else mms to id
            else -> error("unexpected uri $uri")
        }
    }

    private fun requirePermission(permission: String) {
        if (context!!.checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Permission Denial: telephony provider requires $permission")
        }
    }

    private fun isDefaultApp() = context!!.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)

    private fun requireDefaultApp() {
        if (!isDefaultApp()) throw SecurityException("only the default SMS app may create threads")
    }

    private fun matches(row: Map<String, Any?>, selection: String?): Boolean =
        selection == null || selection.split(" AND ").all { clause -> clauseMatches(row, clause.trim()) }

    private fun clauseMatches(row: Map<String, Any?>, clause: String): Boolean {
        IN.matchEntire(clause)?.let { match ->
            val values = match.groupValues[2].split(',').map { it.trim().toLong() }
            return (row[match.groupValues[1]] as Number).toLong() in values
        }
        COMPARE.matchEntire(clause)?.let { match ->
            val actual = (row[match.groupValues[1]] as Number).toLong()
            val expected = match.groupValues[3].toLong()
            return if (match.groupValues[2] == "=") actual == expected else actual != expected
        }
        error("unsupported selection clause: $clause")
    }

    companion object {
        private val IN = Regex("""(\w+) IN \(([\d, ]+)\)""")
        private val COMPARE = Regex("""(\w+) (=|!=) (-?\d+)""")

        private val threads = ArrayList<MutableMap<String, Any?>>()
        private val addresses = ArrayList<MutableMap<String, Any?>>()
        val sms = ArrayList<MutableMap<String, Any?>>()
        private val mms = ArrayList<MutableMap<String, Any?>>()
        private val parts = ArrayList<MutableMap<String, Any?>>()

        fun reset() = listOf(threads, addresses, sms, mms, parts).forEach { it.clear() }

        fun thread(vararg recipients: String): Long {
            val ids = recipients.map { address ->
                addresses.find { it["address"] == address }?.get("_id") as Long?
                    ?: (addresses.size + 1L).also { addresses += mutableMapOf("_id" to it, "address" to address) }
            }
            val id = threads.size + 1L
            threads += mutableMapOf("_id" to id, "recipient_ids" to ids.joinToString(" "))
            return id
        }

        private fun threadFor(recipients: List<String>): Long {
            val ids = recipients.map { address -> addresses.find { it["address"] == address }?.get("_id") }
            if (ids.all { it != null }) {
                val key = ids.joinToString(" ")
                threads.find { it["recipient_ids"] == key }?.let { return it["_id"] as Long }
            }
            return thread(*recipients.toTypedArray())
        }

        fun sms(threadId: Long, body: String, date: Long, type: Int, read: Boolean = true, subId: Int = -1): Long {
            val id = sms.size + 1L
            sms += mutableMapOf(
                "_id" to id, "thread_id" to threadId, "body" to body, "date" to date, "type" to type,
                "read" to if (read) 1 else 0, "sub_id" to subId,
            )
            return id
        }

        fun mms(threadId: Long, dateSeconds: Long, box: Int, read: Boolean = true, text: String? = null, image: Boolean = false): Long {
            val id = mms.size + 1L
            mms += mutableMapOf("_id" to id, "thread_id" to threadId, "date" to dateSeconds, "msg_box" to box, "read" to if (read) 1 else 0, "sub_id" to -1)
            parts += mutableMapOf("_id" to parts.size + 1L, "mid" to id, "ct" to "application/smil", "text" to "<smil/>")
            if (image) parts += mutableMapOf("_id" to parts.size + 1L, "mid" to id, "ct" to "image/jpeg", "text" to null)
            if (text != null) parts += mutableMapOf("_id" to parts.size + 1L, "mid" to id, "ct" to "text/plain", "text" to text)
            return id
        }
    }
}
