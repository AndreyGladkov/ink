package io.github.andreygladkov.inkphone.call

import android.app.Activity
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.ui.iconButton

fun callButton(activity: Activity, number: String) =
    iconButton(activity, R.drawable.ic_call, activity.getString(R.string.call)).apply {
        setOnClickListener { CallLauncher.call(activity, number) }
    }
