package io.github.andreygladkov.inkphone.eink

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import io.github.andreygladkov.inkphone.inkPrefs

data class InkStyle(
    val inverted: Boolean = false,
    val textSizeSp: Float = DEFAULT_TEXT_SIZE_SP,
    val weight: Int = DEFAULT_WEIGHT,
    val cornerRadiusDp: Float = DEFAULT_CORNER_RADIUS_DP,
) {
    val foreground: Int get() = if (inverted) Color.WHITE else Color.BLACK
    val background: Int get() = if (inverted) Color.BLACK else Color.WHITE

    companion object {
        const val PREF_INVERTED = "style_inverted"
        const val PREF_TEXT_SIZE_SP = "style_text_size_sp"
        const val PREF_WEIGHT = "style_weight"
        const val PREF_CORNER_RADIUS_DP = "style_corner_radius_dp"
        const val DEFAULT_TEXT_SIZE_SP = 18f
        const val DEFAULT_CORNER_RADIUS_DP = 8f
        val TEXT_SIZES_SP = listOf(16f, 18f, 20f, 22f, 24f)
        val CORNER_RADII_DP = listOf(0f, 4f, 8f, 12f, 16f)
        const val DEFAULT_WEIGHT = 500
        const val BOLD_WEIGHT = 700

        fun from(prefs: SharedPreferences) = InkStyle(
            inverted = prefs.getBoolean(PREF_INVERTED, false),
            textSizeSp = prefs.getFloat(PREF_TEXT_SIZE_SP, DEFAULT_TEXT_SIZE_SP),
            weight = prefs.getInt(PREF_WEIGHT, DEFAULT_WEIGHT),
            cornerRadiusDp = prefs.getFloat(PREF_CORNER_RADIUS_DP, DEFAULT_CORNER_RADIUS_DP),
        )

        fun of(context: Context) = from(context.inkPrefs())
    }
}
