package io.github.andreygladkov.inkphone.favorites

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.inkPrefs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class SpeedDialTest {
    private val speedDial = SpeedDial(ApplicationProvider.getApplicationContext<Context>().inkPrefs())

    @Test fun assignsAndFindsKeyByAnyNumberFormat() {
        speedDial.assign('2', "+7 701 111 22 33", listOf("+7 701 111 22 33"))
        assertEquals("+7 701 111 22 33", speedDial.numberFor('2'))
        assertEquals('2', speedDial.keyFor(listOf("87011112233")))
    }

    @Test fun reassigningContactMovesItsKey() {
        speedDial.assign('2', "+77011112233", listOf("+77011112233", "+77020000000"))
        speedDial.assign('5', "+77020000000", listOf("+77011112233", "+77020000000"))
        assertNull(speedDial.numberFor('2'))
        assertEquals(mapOf('5' to "+77020000000"), speedDial.assignments())
    }

    @Test fun keyTakenByAnotherContactIsReplaced() {
        speedDial.assign('3', "+77011112233", listOf("+77011112233"))
        speedDial.assign('3', "+77090000000", listOf("+77090000000"))
        assertEquals("+77090000000", speedDial.numberFor('3'))
    }

    @Test fun clearingRemovesAssignment() {
        speedDial.assign('4', "+77011112233", listOf("+77011112233"))
        speedDial.assign(null, "+77011112233", listOf("+77011112233"))
        assertEquals(emptyMap(), speedDial.assignments())
        assertNull(speedDial.numberFor('1'))
    }
}
