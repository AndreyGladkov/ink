package io.github.andreygladkov.ink.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.edit
import io.github.andreygladkov.ink.R
import io.github.andreygladkov.ink.inkPrefs

class PermissionGate(private val activity: Activity, private val permissions: List<String>, private val requestCode: Int) {
    constructor(activity: Activity, permission: String, requestCode: Int) : this(activity, listOf(permission), requestCode)

    val isGranted: Boolean get() = missing().isEmpty()

    fun bind(action: InkButton) {
        val prefs = activity.inkPrefs()
        val missing = missing()
        val deniedForever = missing.any { prefs.getBoolean(askedKey(it), false) && !activity.shouldShowRequestPermissionRationale(it) }
        action.setText(if (deniedForever) R.string.open_settings else R.string.grant_access)
        action.setOnClickListener {
            if (deniedForever) {
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
            } else {
                prefs.edit { missing.forEach { putBoolean(askedKey(it), true) } }
                activity.requestPermissions(missing.toTypedArray(), requestCode)
            }
        }
    }

    private fun missing() = permissions.filter { activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    private fun askedKey(permission: String) = "permission_asked_$permission"
}
