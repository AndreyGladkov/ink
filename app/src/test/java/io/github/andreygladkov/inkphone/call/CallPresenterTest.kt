package io.github.andreygladkov.inkphone.call

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallPresenterTest {
    private val audio = AudioSnapshot()

    private fun call(id: String, phase: CallPhase, canHold: Boolean = true, canMerge: Boolean = false, connectedAt: Long? = null) =
        CallSnapshot(id, "+7701$id", null, phase, connectedAt, canHold, canMerge)

    @Test fun ringingAloneIsIncoming() {
        val model = CallPresenter.present(listOf(call("1", CallPhase.RINGING)), audio, null)
        assertEquals("1", assertIs<CallScreenModel.Incoming>(model).call.id)
    }

    @Test fun ringingDuringCallIsWaiting() {
        val model = CallPresenter.present(listOf(call("1", CallPhase.ACTIVE), call("2", CallPhase.RINGING)), audio, null)
        val waiting = assertIs<CallScreenModel.Waiting>(model)
        assertEquals("2", waiting.incoming.id)
        assertEquals("1", waiting.current.id)
    }

    @Test fun activeAndHeldCanSwap() {
        val model = assertIs<CallScreenModel.InCall>(
            CallPresenter.present(listOf(call("1", CallPhase.HOLDING), call("2", CallPhase.ACTIVE, canMerge = true)), audio, null),
        )
        assertEquals("2", model.primary.id)
        assertEquals("1", model.held?.id)
        assertTrue(model.canSwap)
        assertTrue(model.canMerge)
        assertFalse(model.canHold)
    }

    @Test fun singleHeldCallCanBeResumed() {
        val model = assertIs<CallScreenModel.InCall>(CallPresenter.present(listOf(call("1", CallPhase.HOLDING, canHold = false)), audio, null))
        assertNull(model.held)
        assertTrue(model.canHold)
        assertFalse(model.canSwap)
    }

    @Test fun simSelectionComesFirst() {
        val model = CallPresenter.present(listOf(call("1", CallPhase.ACTIVE), call("2", CallPhase.SELECT_ACCOUNT)), audio, null)
        assertIs<CallScreenModel.SelectAccount>(model)
    }

    @Test fun noCallsShowsLastEndedThenIdle() {
        val ended = call("1", CallPhase.DISCONNECTED)
        assertIs<CallScreenModel.Ended>(CallPresenter.present(emptyList(), audio, ended))
        assertEquals(CallScreenModel.Idle, CallPresenter.present(emptyList(), audio, null))
    }

    @Test fun elapsedMinutesOnlyForActiveCalls() {
        assertEquals(3L, CallPresenter.elapsedMinutes(call("1", CallPhase.ACTIVE, connectedAt = 1_000L), 1_000L + 3 * 60_000 + 59_000))
        assertNull(CallPresenter.elapsedMinutes(call("1", CallPhase.HOLDING, connectedAt = 1_000L), 500_000L))
        assertNull(CallPresenter.elapsedMinutes(call("1", CallPhase.DIALING), 500_000L))
    }
}
