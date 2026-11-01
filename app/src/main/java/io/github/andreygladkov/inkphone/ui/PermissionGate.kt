package io.github.andreygladkov.inkphone.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.edit
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.inkPrefs

class PermissionGate(private val activity: Activity, private val permission: String, private val requestCode: Int) {
    val isGranted: Boolean get() = activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun bind(action: InkButton) {
        val prefs = activity.inkPrefs()
        val askedKey = "permission_asked_$permission"
        val deniedForever = prefs.getBoolean(askedKey, false) && !activity.shouldShowRequestPermissionRationale(permission)
        action.setText(if (deniedForever) R.string.open_settings else R.string.grant_access)
        action.setOnClickListener {
            if (deniedForever) {
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
            } else {
                prefs.edit { putBoolean(askedKey, true) }
                activity.requestPermissions(arrayOf(permission), requestCode)
            }
        }
    }
}
