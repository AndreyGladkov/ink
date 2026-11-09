package io.github.andreygladkov.ink.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.edit
import io.github.andreygladkov.ink.R
import io.github.andreygladkov.ink.eink.A9Panel
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.haptics.HapticStrength
import io.github.andreygladkov.ink.haptics.Haptics
import io.github.andreygladkov.ink.inkPrefs
import kotlin.math.roundToInt

/** Settings rows every ink app shows; each app puts them among its own rows. */
object InkSettings {
    fun textSize(activity: Activity): View = InkChoiceRow(activity, activity.getString(R.string.settings_text_size),
        InkStyle.TEXT_SIZES_SP.map { it to it.roundToInt().toString() }, InkStyle.of(activity).textSizeSp,
    ) { size -> restyle(activity) { putFloat(InkStyle.PREF_TEXT_SIZE_SP, size) } }

    fun weight(activity: Activity): View = InkChoiceRow(activity, activity.getString(R.string.settings_weight),
        listOf(400 to R.string.weight_regular, 500 to R.string.weight_medium, 600 to R.string.weight_semibold).map { (weight, label) -> weight to activity.getString(label) },
        InkStyle.of(activity).weight,
    ) { weight -> restyle(activity) { putInt(InkStyle.PREF_WEIGHT, weight) } }

    fun lists(activity: Activity): View = InkChoiceRow(activity, activity.getString(R.string.settings_lists),
        listOf(false to activity.getString(R.string.lists_pages), true to activity.getString(R.string.lists_scroll)),
        activity.inkPrefs().getBoolean(InkPagedList.PREF_SCROLL, false),
    ) { scroll -> restyle(activity) { putBoolean(InkPagedList.PREF_SCROLL, scroll) } }

    fun cornerRadius(activity: Activity): View = InkChoiceRow(activity, activity.getString(R.string.settings_corner_radius),
        InkStyle.CORNER_RADII_DP.map { it to it.roundToInt().toString() }, InkStyle.of(activity).cornerRadiusDp,
        decorate = { radius -> cornerRadiusDp = radius }, isJoined = false,
    ) { radius -> restyle(activity) { putFloat(InkStyle.PREF_CORNER_RADIUS_DP, radius) } }

    fun colors(activity: Activity): View = InkChoiceRow(activity, activity.getString(R.string.settings_colors),
        listOf(false to activity.getString(R.string.colors_black_on_white), true to activity.getString(R.string.colors_white_on_black)),
        InkStyle.of(activity).inverted,
    ) { inverted -> restyle(activity) { putBoolean(InkStyle.PREF_INVERTED, inverted) } }

    fun tabLabels(activity: Activity): View = onOff(activity, R.string.settings_tab_labels, activity.inkPrefs().getBoolean(InkTabBar.PREF_LABELS, true)) { labels ->
        activity.inkPrefs().edit { putBoolean(InkTabBar.PREF_LABELS, labels) }
    }

    fun onOff(activity: Activity, title: Int, current: Boolean, onChange: (Boolean) -> Unit): View = InkChoiceRow(activity, activity.getString(title),
        listOf(true to activity.getString(R.string.on), false to activity.getString(R.string.off)), current, onChoose = onChange)

    /** A full-width row that opens something or makes a choice of its own, set off from the row above. */
    fun rowButton(context: Context, label: String, onClick: () -> Unit) = InkButton(context).apply {
        text = label
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (ROW_GAP_DP * context.resources.displayMetrics.density).roundToInt()
        }
    }

    fun hint(context: Context, text: String) = InkTextView(context).apply {
        this.text = text
        textScale = InkStyle.SECONDARY_SCALE
        setPadding(0, (ROW_GAP_DP * context.resources.displayMetrics.density).roundToInt(), 0, 0)
    }

    fun addHaptics(activity: Activity, items: InkPagedItems, channels: List<Pair<HapticChannel, String>>) {
        val haptics = Haptics.of(activity)
        items.section(activity.getString(R.string.settings_haptics))
        if (haptics.isDisabledBySystem) {
            items.item { InkTextView(activity).apply { setText(R.string.haptics_disabled_by_system) } }
            items.item {
                InkButton(activity).apply {
                    setText(R.string.open_settings)
                    setOnClickListener { activity.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
            }
        }
        val levels = (HapticStrength.OFF..HapticStrength.MAX).map { it to if (it == HapticStrength.OFF) activity.getString(R.string.off) else it.toString() }
        channels.forEach { (channel, title) ->
            items.item {
                InkChoiceRow(activity, title, levels, haptics.strength(channel)) { level ->
                    activity.inkPrefs().edit { putInt(channel.prefKey, level) }
                    haptics.perform(channel)
                }
            }
        }
    }

    /** Shown only with the A9 accessibility service installed, which performs the panel refreshes. */
    fun addA9(activity: Activity, items: InkPagedItems, appRows: List<() -> View> = emptyList()) {
        if (!A9Panel.isInstalled(activity)) return
        items.section(activity.getString(R.string.settings_a9))
        items.item { hint(activity, activity.getString(R.string.a9_hint)) }
        appRows.forEach(items::item)
        items.item {
            InkChoiceRow(activity, activity.getString(R.string.a9_clear_every_pages),
                A9Panel.PAGE_INTERVALS.map { it to if (it == 0) activity.getString(R.string.off) else it.toString() },
                activity.inkPrefs().getInt(A9Panel.PREF_CLEAR_EVERY_PAGES, 0),
            ) { every -> activity.inkPrefs().edit { putInt(A9Panel.PREF_CLEAR_EVERY_PAGES, every) } }
        }
    }

    fun addAbout(activity: Activity, items: InkPagedItems, about: Class<out InkAboutActivity>) {
        items.section(activity.getString(R.string.about))
        items.item { rowButton(activity, activity.getString(R.string.about)) { activity.startActivity(Intent(activity, about)) } }
    }

    /** Style changes take effect on the next layout, so the screen is rebuilt at once to show them. */
    private fun restyle(activity: Activity, change: SharedPreferences.Editor.() -> Unit) {
        activity.inkPrefs().edit(commit = true, action = change)
        activity.recreate()
    }

    private const val ROW_GAP_DP = 8f
}
