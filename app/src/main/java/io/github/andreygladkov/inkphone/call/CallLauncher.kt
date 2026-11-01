package io.github.andreygladkov.inkphone.call

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.TelecomManager

object CallLauncher {
    const val REQUEST_CALL_PHONE = 41
    const val REQUEST_DIALER_ROLE = 42
    private var pendingNumber: String? = null

    fun call(activity: Activity, number: String) {
        if (activity.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingNumber = number
            activity.requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL_PHONE)
            return
        }
        place(activity, Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null))
    }

    fun callVoicemail(activity: Activity) {
        if (activity.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return
        place(activity, Uri.fromParts(PhoneAccount.SCHEME_VOICEMAIL, "", null))
    }

    fun onPermissionsResult(activity: Activity, requestCode: Int, grantResults: IntArray) {
        if (requestCode != REQUEST_CALL_PHONE) return
        val number = pendingNumber
        pendingNumber = null
        if (number != null && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) call(activity, number)
    }

    fun isDefaultDialer(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)

    fun requestDefaultDialer(activity: Activity) {
        val roles = activity.getSystemService(RoleManager::class.java)
        if (roles.isRoleAvailable(RoleManager.ROLE_DIALER) && !roles.isRoleHeld(RoleManager.ROLE_DIALER)) {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER), REQUEST_DIALER_ROLE)
        }
    }

    @Suppress("MissingPermission")
    private fun place(context: Context, uri: Uri) {
        context.getSystemService(TelecomManager::class.java).placeCall(uri, Bundle())
    }
}
