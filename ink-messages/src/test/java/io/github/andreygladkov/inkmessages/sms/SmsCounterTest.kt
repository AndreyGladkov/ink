package io.github.andreygladkov.inkmessages.sms

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmsCounterTest {
    @Test fun hiddenForShortMessages() {
        assertFalse(SmsCounter.isWorthShowing("", 1, 70))
        assertFalse(SmsCounter.isWorthShowing("Привет", 1, 64))
    }

    @Test fun shownNearTheBoundaryAndForLongMessages() {
        assertTrue(SmsCounter.isWorthShowing("x", 1, 9))
        assertTrue(SmsCounter.isWorthShowing("x", 2, 120))
    }
}
