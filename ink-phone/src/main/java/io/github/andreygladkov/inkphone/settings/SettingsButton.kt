package io.github.andreygladkov.inkphone.settings

import android.app.Activity
import android.content.Intent
import io.github.andreygladkov.ink.ui.InkHeader

fun settingsButton(activity: Activity) = InkHeader.settings(activity) { activity.startActivity(Intent(activity, SettingsActivity::class.java)) }
