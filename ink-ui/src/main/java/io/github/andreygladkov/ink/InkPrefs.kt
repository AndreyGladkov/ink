package io.github.andreygladkov.ink

import android.content.Context
import android.content.SharedPreferences

fun Context.inkPrefs(): SharedPreferences = getSharedPreferences(getString(R.string.ink_prefs_file), Context.MODE_PRIVATE)
