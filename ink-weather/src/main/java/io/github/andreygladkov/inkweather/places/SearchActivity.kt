package io.github.andreygladkov.inkweather.places

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.core.widget.doAfterTextChanged
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.inkweather.R
import io.github.andreygladkov.inkweather.forecast.Place
import io.github.andreygladkov.inkweather.forecast.Weather
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class SearchActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val store by lazy { PlaceStore.of(this) }
    private lateinit var field: InkEditText
    private lateinit var list: InkPagedList
    private lateinit var status: InkTextView
    private var asked = 0
    private var searched: String? = null
    private val search = Runnable { search() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        field = InkEditText(this).apply {
            isBordered = true
            hint = getString(R.string.search_hint)
            contentDescription = hint
            doAfterTextChanged {
                main.removeCallbacks(search)
                main.postDelayed(search, TYPING_PAUSE_MS)
            }
            setOnEditorActionListener { _, action, _ ->
                if (action != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
                main.removeCallbacks(search)
                search()
                true
            }
        }
        list = InkPagedList(this)
        status = InkTextView(this).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
            setBackgroundColor(InkStyle.of(this@SearchActivity).background)
        }
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkHeader.of(this@SearchActivity, FrameLayout(this@SearchActivity).apply {
                setPadding(0, 0, dp(PADDING_DP), 0)
                addView(field)
            }, InkHeader.back(this@SearchActivity, ::finish)))
            addView(FrameLayout(this@SearchActivity).apply {
                addView(list)
                addView(status)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        field.requestFocus()
    }

    override fun onDestroy() {
        main.removeCallbacks(search)
        super.onDestroy()
    }

    private fun search() {
        val query = field.text.toString().trim()
        if (query == searched) return
        searched = query
        val request = ++asked
        if (query.length < MIN_QUERY) return show(emptyList())
        showStatus(R.string.searching)
        val language = resources.configuration.locales[0].language
        thread {
            val found = Weather.search(query, language)
            main.post {
                if (isDestroyed || request != asked) return@post
                when (found) {
                    is Weather.Found.Places -> show(found.places)
                    is Weather.Found.Failed -> {
                        searched = null
                        showStatus(if (found.failure == Weather.Failure.NETWORK) R.string.error_network else R.string.error_service)
                    }
                }
            }
        }
    }

    private fun show(places: List<Place>) {
        list.setAdapter(object : InkPagedList.Adapter {
            override val count = places.size
            override fun itemId(position: Int) = places[position].id
            override fun createView(position: Int): View = placeButton(this@SearchActivity, places[position]) { choose(places[position]) }
        })
        if (places.isEmpty() && (searched?.length ?: 0) >= MIN_QUERY) showStatus(R.string.search_nothing) else status.visibility = View.GONE
    }

    private fun showStatus(@StringRes message: Int) {
        status.setText(message)
        status.visibility = View.VISIBLE
    }

    private fun choose(place: Place) {
        store.add(place)
        setResult(RESULT_OK, Intent().putExtra(EXTRA_PLACE, place.id))
        finish()
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_PLACE = "place"
        private const val NONE = -1L
        /** Results wait for a pause in typing, since every redraw costs the e-ink panel a refresh. */
        private const val TYPING_PAUSE_MS = 500L
        /** The geocoding service answers a single letter with nothing. */
        private const val MIN_QUERY = 2
        private const val PADDING_DP = 12f

        fun intent(context: Context) = Intent(context, SearchActivity::class.java)

        fun chosen(result: Intent?): Long? = result?.getLongExtra(EXTRA_PLACE, NONE)?.takeIf { it != NONE }
    }
}
