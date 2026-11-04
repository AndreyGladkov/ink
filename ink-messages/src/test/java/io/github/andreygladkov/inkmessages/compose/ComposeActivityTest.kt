package io.github.andreygladkov.inkmessages.compose

import android.content.Intent
import android.net.Uri
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.inkmessages.TestData
import io.github.andreygladkov.inkmessages.TestData.context
import io.github.andreygladkov.inkmessages.data.FakePhoneLookupProvider
import io.github.andreygladkov.inkmessages.thread.ThreadActivity
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class ComposeActivityTest {
    @Before fun setUp() {
        TestData.installProviders()
        TestData.makeDefaultSmsApp()
        FakePhoneLookupProvider.add("+79001112233", "Мама", starred = true)
        FakePhoneLookupProvider.add("+79004445566", "Петя")
    }

    @Test fun searchingAContactOpensItsThread() {
        val activity = Robolectric.buildActivity(ComposeActivity::class.java).setup().get()
        val root = contentRoot(activity)
        idleUntil { root.find<InkButton>().any { it.text.startsWith("Мама") } }
        root.find<InkEditText>().single().setText("пет")
        val rows = root.find<InkButton>().filter { it.text.contains("+7") }
        assertEquals(1, rows.size, rows.map { it.text }.toString())
        assertPureBlackAndWhite(render(root))
        rows.single().performClick()
        idleUntil { shadowOf(activity).peekNextStartedActivity() != null }
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ThreadActivity::class.java.name, started.component?.className)
        assertTrue(started.getLongExtra("thread_id", -1) > 0)
        assertTrue(activity.isFinishing)
    }

    @Test fun typedNumberCanBeWrittenTo() {
        val activity = Robolectric.buildActivity(ComposeActivity::class.java).setup().get()
        val root = contentRoot(activity)
        root.find<InkEditText>().single().setText("+7 999 000 11 22")
        idleUntil { root.find<InkButton>().any { it.text.startsWith("Написать на") } }
    }

    @Test fun sendToIntentOpensTheThreadWithTheBody() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+79001112233?body=Перезвони"))
        val activity = Robolectric.buildActivity(ComposeActivity::class.java, intent).setup().get()
        idleUntil { shadowOf(activity).peekNextStartedActivity() != null }
        val started = shadowOf(activity).nextStartedActivity
        assertEquals("Перезвони", started.getStringExtra("body"))
        assertTrue(activity.isFinishing)
    }

    @Test fun recipientAndBodyAreReadFromSmsUris() {
        assertEquals("+79001112233", ComposeActivity.recipientOf(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:%2B79001112233;+79004445566"))))
        assertEquals("Привет", ComposeActivity.bodyOf(Intent(Intent.ACTION_SENDTO, Uri.parse("sms:900")).putExtra("sms_body", "Привет")))
    }
}
