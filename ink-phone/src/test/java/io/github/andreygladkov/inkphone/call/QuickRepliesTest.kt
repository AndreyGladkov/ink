package io.github.andreygladkov.inkphone.call

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.inkPrefs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class QuickRepliesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun startsWithFourDefaults() {
        assertEquals(4, QuickReplies.of(context).all().size)
    }

    @Test fun keepsAnEditedReplyAndFallsBackWhenItIsCleared() {
        val replies = QuickReplies(context.inkPrefs(), listOf("Перезвоню", "Я за рулём"))
        replies.set(1, "  Я в метро  ")
        assertEquals(listOf("Перезвоню", "Я в метро"), replies.all())
        replies.set(1, " ")
        assertEquals(listOf("Перезвоню", "Я за рулём"), replies.all())
    }
}
