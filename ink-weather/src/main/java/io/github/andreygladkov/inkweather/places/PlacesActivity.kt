package io.github.andreygladkov.inkweather.places

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.ink.ui.confirmBeforeAction
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.inkweather.R
import io.github.andreygladkov.inkweather.forecast.Place

class PlacesActivity : Activity() {
    private val store by lazy { PlaceStore.of(this) }
    private lateinit var list: PlaceList

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        list = PlaceList(this, getString(R.string.places)) { startActivityForResult(SearchActivity.intent(this), REQUEST_ADD) }
    }

    override fun onResume() {
        super.onResume()
        showRows()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val added = SearchActivity.chosen(data) ?: return
        store.show(added)
        finish()
    }

    private fun showRows() {
        list.rows.removeAllViews()
        store.places.forEachIndexed { index, place -> list.rows.addView(row(place, isFirst = index == 0)) }
    }

    private fun row(place: Place, isFirst: Boolean) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(placeButton(this@PlacesActivity, place) {
            store.show(place.id)
            finish()
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(iconButton(this@PlacesActivity, R.drawable.ic_arrow_upward, getString(R.string.move_up)).apply {
            visibility = if (isFirst) View.INVISIBLE else View.VISIBLE
            setOnClickListener {
                store.moveUp(place.id)
                showRows()
            }
        })
        addView(iconButton(this@PlacesActivity, R.drawable.ic_delete, getString(R.string.delete_place)).apply {
            confirmBeforeAction(R.string.delete_place_confirm) {
                store.remove(place.id)
                showRows()
            }
        })
    }

    companion object {
        private const val REQUEST_ADD = 1

        fun intent(context: Context) = Intent(context, PlacesActivity::class.java)
    }
}
