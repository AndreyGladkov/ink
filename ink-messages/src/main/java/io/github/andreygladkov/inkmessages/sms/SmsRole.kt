package io.github.andreygladkov.inkmessages.sms

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context

object SmsRole {
    const val REQUEST_ROLE = 21

    fun isDefault(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true

    fun request(activity: Activity) {
        val roles = activity.getSystemService(RoleManager::class.java) ?: return
        @Suppress("DEPRECATION")
        activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_SMS), REQUEST_ROLE)
    }
}
