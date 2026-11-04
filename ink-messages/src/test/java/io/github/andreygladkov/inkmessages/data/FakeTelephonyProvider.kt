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
import android.os.ParcelFileDescriptor
import android.provider.Telephony.TextBasedSmsColumns
import java.io.File
import java.io.FileNotFoundException

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
        val rows = table.filter { row -> (id == null || row["_id"] == id) && matches(row, selection, args) }
        return MatrixCursor(projection).apply { rows.forEach { row -> addRow(projection!!.map { row[it] }) } }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        requirePermission(Manifest.permission.READ_SMS)
        if (!isDefaultApp()) return null
        if (uri.authority == "mms") return insertMms(uri, values!!)
        require(uri.authority == "sms") { "only SMS and MMS inserts are faked: $uri" }
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
        val rows = table.filter { row -> (id == null || row["_id"] == id) && matches(row, selection, selectionArgs) }
        rows.forEach { row -> values!!.keySet().forEach { key -> row[key] = values.get(key) } }
        if (rows.isNotEmpty()) context!!.contentResolver.notifyChange(uri, null)
        return rows.size
    }

    override fun getType(uri: Uri): String? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        requirePermission(Manifest.permission.READ_SMS)
        if (!isDefaultApp()) return 0
        val id = uri.lastPathSegment!!.toLong()
        val removed = when (uri.authority) {
            "sms" -> if (sms.removeAll { it["_id"] == id }) 1 else 0
            "mms" -> removeMms { it["_id"] == id }
            "mms-sms" -> {
                require(uri.pathSegments.first() == "conversations") { "unexpected delete $uri" }
                val count = sms.count { it["thread_id"] == id }
                sms.removeAll { it["thread_id"] == id }
                count + removeMms { it["thread_id"] == id }
            }
            else -> error("unexpected delete $uri")
        }
        if (removed > 0) context!!.contentResolver.notifyChange(uri, null)
        return removed
    }

    private fun removeMms(which: (Map<String, Any?>) -> Boolean): Int {
        val ids = mms.filter(which).map { it["_id"] }
        mms.removeAll { it["_id"] in ids }
        parts.removeAll { it["mid"] in ids }
        addrs.removeAll { it["msg_id"] in ids }
        return ids.size
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        requirePermission(Manifest.permission.READ_SMS)
        require(uri.authority == "mms" && uri.pathSegments.firstOrNull() == "part") { "only MMS part files are faked: $uri" }
        val id = uri.lastPathSegment!!.toLong()
        val file = if (mode.contains('w')) {
            check(isDefaultApp()) { "only the default SMS app may write parts" }
            partFiles.getOrPut(id) { File.createTempFile("part", ".bin") }
        } else {
            partFiles[id] ?: throw FileNotFoundException(uri.toString())
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    private fun insertMms(uri: Uri, values: ContentValues): Uri {
        val row = values.keySet().associateWith { values.get(it) }.toMutableMap()
        val segments = uri.pathSegments
        return when {
            segments == listOf("inbox") -> {
                val id = ++lastMmsId
                row["_id"] = id
                row["msg_box"] = 1
                row.putIfAbsent("sub_id", -1)
                mms += row
                context!!.contentResolver.notifyChange(uri, null)
                Uri.parse("content://mms/inbox/$id")
            }
            segments.getOrNull(1) == "addr" -> {
                row["msg_id"] = segments[0].toLong()
                addrs += row
                Uri.parse("content://mms/${segments[0]}/addr/${addrs.size}")
            }
            segments.getOrNull(1) == "part" -> {
                val id = (parts.maxOfOrNull { it["_id"] as Long } ?: 0L) + 1
                row["_id"] = id
                row["mid"] = segments[0].toLong()
                parts += row
                Uri.parse("content://mms/part/$id")
            }
            else -> error("unexpected MMS insert $uri")
        }
    }

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
            "mms" -> when {
                segments.firstOrNull() == "part" -> parts to segments.getOrNull(1)?.toLong()
                segments.getOrNull(1) == "addr" -> addrs to null
                else -> mms to id
            }
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

    private fun matches(row: Map<String, Any?>, selection: String?, args: Array<out String>? = null): Boolean {
        if (selection == null) return true
        val remaining = args.orEmpty().iterator()
        return selection.split(" AND ").all { clause -> clauseMatches(row, clause.trim(), remaining) }
    }

    private fun clauseMatches(row: Map<String, Any?>, clause: String, args: Iterator<String>): Boolean {
        ARGUMENT.matchEntire(clause)?.let { match -> return row[match.groupValues[1]]?.toString() == args.next() }
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
        private val ARGUMENT = Regex("""(\w+) = \?""")
        private val IN = Regex("""(\w+) IN \(([\d, ]+)\)""")
        private val COMPARE = Regex("""(\w+) (=|!=) (-?\d+)""")

        private val threads = ArrayList<MutableMap<String, Any?>>()
        private val addresses = ArrayList<MutableMap<String, Any?>>()
        val sms = ArrayList<MutableMap<String, Any?>>()
        val mms = ArrayList<MutableMap<String, Any?>>()
        val parts = ArrayList<MutableMap<String, Any?>>()
        val addrs = ArrayList<MutableMap<String, Any?>>()
        private val partFiles = HashMap<Long, File>()

        // Like the real provider, ids are never reused, so state keyed by message id cannot leak between tests.
        private var lastMmsId = 0L

        fun reset() {
            listOf(threads, addresses, sms, mms, parts, addrs).forEach { it.clear() }
            partFiles.values.forEach(File::delete)
            partFiles.clear()
        }

        fun recipientsOf(threadId: Long): List<String> {
            val ids = (threads.single { it["_id"] == threadId }["recipient_ids"] as String).split(' ').map(String::toLong)
            return ids.map { id -> addresses.single { it["_id"] == id }["address"] as String }
        }

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

        fun mms(
            threadId: Long, dateSeconds: Long, box: Int, read: Boolean = true, text: String? = null, image: Boolean = false,
            imageBytes: ByteArray? = null, extraParts: List<Pair<String, ByteArray>> = emptyList(),
        ): Long {
            val id = ++lastMmsId
            mms += mutableMapOf("_id" to id, "thread_id" to threadId, "date" to dateSeconds, "msg_box" to box, "read" to if (read) 1 else 0, "sub_id" to -1, "m_type" to 132)
            parts += mutableMapOf("_id" to parts.size + 1L, "mid" to id, "ct" to "application/smil", "text" to "<smil/>")
            if (image || imageBytes != null) addPart(id, "image/jpeg", imageBytes)
            extraParts.forEach { (type, bytes) -> addPart(id, type, bytes) }
            if (text != null) parts += mutableMapOf("_id" to parts.size + 1L, "mid" to id, "ct" to "text/plain", "text" to text)
            return id
        }

        fun pendingMms(threadId: Long, dateSeconds: Long, sizeBytes: Long, failed: Boolean = false): Long {
            val id = ++lastMmsId
            mms += mutableMapOf(
                "_id" to id, "thread_id" to threadId, "date" to dateSeconds, "msg_box" to 1, "read" to 0, "sub_id" to -1,
                "m_type" to 130, "m_size" to sizeBytes, "retr_st" to if (failed) 192 else null,
                "ct_l" to "http://mmsc.example/$id", "tr_id" to "tr$id",
            )
            return id
        }

        private fun addPart(messageId: Long, type: String, bytes: ByteArray?) {
            val partId = parts.size + 1L
            parts += mutableMapOf("_id" to partId, "mid" to messageId, "ct" to type, "text" to null)
            if (bytes != null) partFiles[partId] = File.createTempFile("part", ".bin").apply { writeBytes(bytes) }
        }
    }
}
