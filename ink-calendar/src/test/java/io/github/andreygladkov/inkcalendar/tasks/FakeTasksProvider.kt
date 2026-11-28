package io.github.andreygladkov.inkcalendar.tasks

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

class FakeTasksProvider : ContentProvider() {
    private val rows = mutableListOf<Array<Any?>>()
    var queries = 0
        private set

    fun task(title: String, due: String, list: String = "Дом", reminder: String? = null) {
        rows += arrayOf(list, "- [ ] $title", 0, title, due, reminder)
    }

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        queries++
        return MatrixCursor(arrayOf("list", "line", "occurrence", "title", "due", "reminder")).apply { rows.forEach(::addRow) }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        fun install(application: Application, granted: Boolean = true): FakeTasksProvider {
            shadowOf(application.packageManager).installPackage(PackageInfo().apply { packageName = InkTasks.PACKAGE })
            if (granted) shadowOf(application).grantPermissions(InkTasks.PERMISSION)
            return Robolectric.setupContentProvider(FakeTasksProvider::class.java, InkTasks.URI.authority)
        }
    }
}
