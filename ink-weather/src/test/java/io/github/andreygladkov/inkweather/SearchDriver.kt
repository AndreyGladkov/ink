package io.github.andreygladkov.inkweather

import android.content.Intent
import android.view.inputmethod.EditorInfo
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.inkweather.forecast.Place
import io.github.andreygladkov.inkweather.places.SearchActivity
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

fun findInSearch(asked: Intent, place: Place): Intent {
    val search = Robolectric.buildActivity(SearchActivity::class.java, asked).setup().get()
    contentRoot(search).find<InkEditText>().single().apply {
        setText(place.name)
        onEditorAction(EditorInfo.IME_ACTION_SEARCH)
    }
    idleUntil { contentRoot(search).find<InkButton>().any { it.text.startsWith(place.name) } }
    contentRoot(search).find<InkButton>().first { it.text.startsWith(place.name) }.performClick()
    return shadowOf(search).resultIntent
}
