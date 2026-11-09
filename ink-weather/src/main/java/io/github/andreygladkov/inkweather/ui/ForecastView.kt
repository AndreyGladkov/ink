package io.github.andreygladkov.inkweather.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.eink.MonochromeIcons
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkweather.R
import io.github.andreygladkov.inkweather.forecast.Forecast
import io.github.andreygladkov.inkweather.forecast.Weather
import io.github.andreygladkov.inkweather.forecast.WeatherFormat
import java.time.Instant
import kotlin.math.roundToInt

/** Everything fits one screen without scrolling: a scrolled e-ink panel smears until its next full refresh. */
class ForecastView(context: Context, private val format: WeatherFormat, private val onRetry: () -> Unit) : LinearLayout(context) {
    private val style = InkStyle.of(context)
    private val density = resources.displayMetrics.density

    init {
        orientation = VERTICAL
        val padding = dp(PADDING_DP)
        setPadding(padding, 0, padding, dp(GAP_DP))
    }

    fun show(forecast: Forecast?, loading: Boolean, failure: Weather.Failure?, now: Instant) {
        removeAllViews()
        val current = forecast?.current(now)
        gravity = if (current == null) Gravity.CENTER else Gravity.TOP
        if (forecast == null || current == null) return showMessage(loading, failure)
        addView(current(current))
        addView(details(forecast, current, now), wide().apply { bottomMargin = dp(SECTION_GAP_DP) })
        addView(InkHeader.line(context))
        addView(hours(forecast, now), wide().apply { topMargin = dp(GAP_DP); bottomMargin = dp(GAP_DP) })
        addView(InkHeader.line(context))
        addView(days(forecast, now), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(text(context.getString(R.string.attribution), ATTRIBUTION_SCALE).apply { gravity = Gravity.CENTER_HORIZONTAL }, wide())
    }

    private fun showMessage(loading: Boolean, failure: Weather.Failure?) {
        val message = when {
            loading || failure == null -> R.string.loading
            failure == Weather.Failure.NETWORK -> R.string.error_network
            failure == Weather.Failure.STORAGE -> R.string.error_storage
            else -> R.string.error_service
        }
        addView(text(context.getString(message)).apply { gravity = Gravity.CENTER }, wide())
        if (!loading && failure != null) {
            addView(InkButton(context).apply {
                setText(R.string.retry)
                setOnClickListener { onRetry() }
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(SECTION_GAP_DP)
            })
        }
    }

    private fun current(now: Forecast.Hour) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(GAP_DP), 0, dp(GAP_DP))
        addView(icon(now.symbol.icon, NOW_ICON_DP), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(PADDING_DP) })
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(text(format.temperature(now.temperature), TEMPERATURE_SCALE, bold = true))
            addView(text(format.description(now.symbol)))
        })
    }

    private fun details(forecast: Forecast, current: Forecast.Hour, now: Instant) = LinearLayout(context).apply {
        orientation = VERTICAL
        listOfNotNull(
            current.feelsLike?.let(format::feelsLike),
            current.windSpeed?.let { format.wind(it, current.windDegrees) },
            current.pressureHpa?.let(format::pressure),
            current.precipitationMm?.let(format::precipitation),
            forecast.takeIf { it.age(now) > Weather.STALE_AFTER }?.let { format.updated(it.fetchedAt, now) },
        ).forEach { addView(text(it, InkStyle.SECONDARY_SCALE)) }
    }

    private fun hours(forecast: Forecast, now: Instant) = LinearLayout(context).apply {
        forecast.hoursAfter(now, HOURS, HOUR_STEP).forEach { hour ->
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(centered(format.hour(hour.time)))
                addView(icon(hour.symbol.icon, SMALL_ICON_DP), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(GAP_DP)
                    bottomMargin = dp(GAP_DP)
                })
                addView(centered(format.temperature(hour.temperature), bold = true))
                addView(centered(format.number(hour.precipitationMm).orEmpty()))
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun days(forecast: Forecast, now: Instant) = LinearLayout(context).apply {
        orientation = VERTICAL
        val today = forecast.localTime(now).toLocalDate()
        val rangeWidth = width(text(format.range(WIDEST_TEMPERATURE, WIDEST_TEMPERATURE)))
        val amountWidth = width(text(format.amount(WIDEST_AMOUNT).orEmpty(), InkStyle.SECONDARY_SCALE))
        forecast.daysFrom(now, DAYS).forEach { day ->
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text(format.day(day.date, today)).apply { maxLines = 1 }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                addView(text(format.amount(day.precipitationMm).orEmpty(), InkStyle.SECONDARY_SCALE).apply { gravity = Gravity.END },
                    LayoutParams(amountWidth, LayoutParams.WRAP_CONTENT))
                addView(icon(day.symbol.icon, SMALL_ICON_DP), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(SECTION_GAP_DP)
                    marginEnd = dp(SECTION_GAP_DP)
                })
                addView(text(format.range(day.max, day.min)).apply { gravity = Gravity.END }, LayoutParams(rangeWidth, LayoutParams.WRAP_CONTENT))
            }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun icon(@DrawableRes res: Int, sizeDp: Float): View = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER
        setImageBitmap(MonochromeIcons.bitmap(context, res, dp(sizeDp), style.foreground))
    }

    private fun text(value: String, scale: Float = 1f, bold: Boolean = false) = InkTextView(context).apply {
        text = value
        textScale = scale
        isBold = bold
    }

    private fun width(view: InkTextView) = view.paint.measureText(view.text.toString()).roundToInt() + 1

    private fun centered(value: String, bold: Boolean = false) = text(value, InkStyle.SECONDARY_SCALE, bold).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        maxLines = 1
    }

    private fun wide() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    private fun dp(value: Float) = (value * density).roundToInt()

    private companion object {
        const val HOURS = 8
        const val HOUR_STEP = 3L
        const val DAYS = 7
        const val PADDING_DP = 16f
        const val GAP_DP = 4f
        const val SECTION_GAP_DP = 8f
        const val NOW_ICON_DP = 64f
        const val SMALL_ICON_DP = 28f
        const val TEMPERATURE_SCALE = 2.5f
        const val ATTRIBUTION_SCALE = 0.7f
        const val WIDEST_TEMPERATURE = -88.0
        const val WIDEST_AMOUNT = 88.8
    }
}
