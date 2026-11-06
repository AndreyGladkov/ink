package io.github.andreygladkov.inkphone.call

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.widget.LinearLayout
import io.github.andreygladkov.ink.ui.VerticalLineDrawable
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.inkphone.R

private fun callButton(activity: Activity, number: String) =
    iconButton(activity, R.drawable.ic_call, activity.getString(R.string.call)).apply {
        setOnClickListener { CallLauncher.call(activity, number) }
    }

private fun smsButton(activity: Activity, number: String) =
    iconButton(activity, R.drawable.ic_sms, activity.getString(R.string.sms)).apply {
        setOnClickListener { openMessages(activity, number) }
    }

fun messageAndCallButtons(activity: Activity, number: String) = LinearLayout(activity).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    dividerDrawable = VerticalLineDrawable(activity)
    showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    addView(smsButton(activity, number))
    addView(callButton(activity, number))
}

/** Opens the default SMS app, which is Ink Messages when the user chose it. */
private fun openMessages(activity: Activity, number: String) {
    try {
        activity.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))
    } catch (_: ActivityNotFoundException) {
        Unit
    }
}
