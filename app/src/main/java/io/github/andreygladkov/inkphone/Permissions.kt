package io.github.andreygladkov.inkphone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

fun Context.hasPermission(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

fun Context.canReadContacts() = hasPermission(Manifest.permission.READ_CONTACTS)
