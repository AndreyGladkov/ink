package io.github.andreygladkov.inkphone

import android.content.Context
import android.content.SharedPreferences

fun Context.inkPrefs(): SharedPreferences = getSharedPreferences("ink_phone", Context.MODE_PRIVATE)
