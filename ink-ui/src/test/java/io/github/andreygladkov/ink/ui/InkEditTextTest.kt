package io.github.andreygladkov.ink.ui

import android.content.Context
import android.text.InputType
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class InkEditTextTest {
    @Test fun spellCheckStaysOffEvenWhenTheKeyboardMaySuggest() {
        val field = InkEditText(ApplicationProvider.getApplicationContext<Context>())
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        assertFalse(field.isSuggestionsEnabled)
    }
}
