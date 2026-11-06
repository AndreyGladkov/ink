package io.github.andreygladkov.ink

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager

fun Context.inkPrefs(): SharedPreferences = lockSafeStorage().getSharedPreferences(getString(R.string.ink_prefs_file), Context.MODE_PRIVATE)

/**
 * Before the first unlock after a reboot, opening credential-protected preferences throws, yet an alarm screen
 * must still draw. Until then the empty device-protected copy is read, so screens fall back to the default style.
 */
fun Context.lockSafeStorage(): Context =
    if (getSystemService(UserManager::class.java).isUserUnlocked) this else createDeviceProtectedStorageContext()
