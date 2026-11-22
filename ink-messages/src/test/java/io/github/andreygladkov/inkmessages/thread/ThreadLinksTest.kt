package io.github.andreygladkov.inkmessages.thread

import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.text.Spanned
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.ViewConfiguration
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.inkmessages.TestData
import io.github.andreygladkov.inkmessages.TestData.INBOX
import io.github.andreygladkov.inkmessages.TestData.context
import io.github.andreygladkov.inkmessages.data.FakeTelephonyProvider
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class ThreadLinksTest {
    private val now = System.currentTimeMillis()
    private lateinit var activity: ThreadActivity

    @Before fun setUp() {
        TestData.installProviders()
        val thread = FakeTelephonyProvider.thread("+79001112233")
        listOf(PLAIN, IN_THE_MIDDLE, AT_THE_END, PHONE).forEachIndexed { index, text ->
            FakeTelephonyProvider.sms(thread, text, now - 60_000 + index * 1000L, INBOX)
        }
        activity = Robolectric.buildActivity(ThreadActivity::class.java, ThreadActivity.intent(context, thread)).setup().get()
        idleUntil { contentRoot(activity).find<BubbleView>().size == 4 }
    }

    @Test fun tappingALinkOpensIt() {
        val bubble = bubble(IN_THE_MIDDLE)
        tap(bubble, linkPoint(bubble))
        val view = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(LINK, view.dataString)
    }

    @Test fun tappingAPhoneNumberOpensTheDialer() {
        val bubble = bubble(PHONE)
        tap(bubble, linkPoint(bubble))
        assertEquals("tel:+79001112233", shadowOf(activity).nextStartedActivity.dataString)
    }

    @Test fun tappingTheTextAroundALinkOpensNothing() {
        val bubble = bubble(IN_THE_MIDDLE)
        tap(bubble, pointAt(bubble, 2))
        val afterLink = pointAt(bubble, linkRange(bubble).second)
        tap(bubble, afterLink.first + 1 to afterLink.second)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun tappingThePaddingBelowALinkOpensNothing() {
        val bubble = bubble(AT_THE_END)
        tap(bubble, pointAt(bubble, linkRange(bubble).second - 2).first to bubble.height - 2f)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun longPressOnALinkSelectsTheMessageWithoutOpeningIt() {
        val bubble = bubble(IN_THE_MIDDLE)
        touch(bubble, MotionEvent.ACTION_DOWN, linkPoint(bubble))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 100L))
        touch(bubble, MotionEvent.ACTION_UP, linkPoint(bubble))
        shadowOf(Looper.getMainLooper()).idle()
        bubble.performClick()
        assertTrue(contentRoot(activity).find<BubbleRow>().single { IN_THE_MIDDLE in it.find<BubbleView>().single().text }.isHighlighted)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun aClickWithoutATapOpensNothing() {
        val bubble = bubble(IN_THE_MIDDLE)
        tap(bubble, linkPoint(bubble))
        shadowOf(activity).nextStartedActivity
        bubble.performClick()
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun onlyBubblesWithLinksAreClickable() {
        assertFalse(bubble(PLAIN).isClickable)
        assertTrue(bubble(IN_THE_MIDDLE).isClickable)
    }

    private fun bubble(text: String) = contentRoot(activity).find<BubbleView>().single { it.text.toString() == text }

    private fun linkRange(bubble: BubbleView): Pair<Int, Int> {
        val text = bubble.text as Spanned
        val span = text.getSpans(0, text.length, URLSpan::class.java).single()
        return text.getSpanStart(span) to text.getSpanEnd(span)
    }

    private fun linkPoint(bubble: BubbleView) = pointAt(bubble, linkRange(bubble).first + 3)

    private fun pointAt(bubble: BubbleView, offset: Int): Pair<Float, Float> {
        val layout = bubble.layout
        val line = layout.getLineForOffset(offset)
        val x = bubble.totalPaddingLeft + layout.getPrimaryHorizontal(offset)
        val y = bubble.totalPaddingTop + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
        return x to y
    }

    private fun tap(bubble: BubbleView, point: Pair<Float, Float>) {
        touch(bubble, MotionEvent.ACTION_DOWN, point)
        touch(bubble, MotionEvent.ACTION_UP, point)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun touch(bubble: BubbleView, action: Int, point: Pair<Float, Float>) {
        val time = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(time, time, action, point.first, point.second, 0)
        bubble.dispatchTouchEvent(event)
        event.recycle()
    }

    private companion object {
        const val LINK = "https://hh.kz/vacancy/1"
        const val PLAIN = "Привет"
        const val IN_THE_MIDDLE = "Откликнитесь на вакансию $LINK до пятницы"
        const val AT_THE_END = "Подробнее: $LINK"
        const val PHONE = "Перезвоните на +7 900 111-22-33 сегодня"
    }
}
