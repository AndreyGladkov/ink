package io.github.andreygladkov.inkphone.call

enum class CallPhase { NEW, RINGING, DIALING, CONNECTING, SELECT_ACCOUNT, ACTIVE, HOLDING, DISCONNECTING, DISCONNECTED }

enum class AudioRoute { EARPIECE, SPEAKER, BLUETOOTH, WIRED_HEADSET }

data class CallSnapshot(
    val id: String,
    val number: String?,
    val name: String?,
    val phase: CallPhase,
    val connectedAtMillis: Long?,
    val canHold: Boolean = false,
    val canMerge: Boolean = false,
    val isConference: Boolean = false,
    val canRespondViaText: Boolean = false,
) {
    val title: String? get() = name ?: number
}

data class AudioSnapshot(
    val muted: Boolean = false,
    val route: AudioRoute = AudioRoute.EARPIECE,
    val supportedRoutes: Set<AudioRoute> = setOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER),
)

sealed interface CallScreenModel {
    data class SelectAccount(val call: CallSnapshot) : CallScreenModel
    data class Incoming(val call: CallSnapshot) : CallScreenModel
    data class Waiting(val incoming: CallSnapshot, val current: CallSnapshot) : CallScreenModel
    data class InCall(
        val primary: CallSnapshot,
        val held: CallSnapshot?,
        val audio: AudioSnapshot,
    ) : CallScreenModel {
        val canSwap: Boolean get() = held != null && primary.phase == CallPhase.ACTIVE
        val canMerge: Boolean get() = held != null && primary.canMerge
        val canHold: Boolean get() = held == null && (primary.canHold || primary.phase == CallPhase.HOLDING)
    }
    data class Ended(val call: CallSnapshot) : CallScreenModel
    data object Idle : CallScreenModel
}

object CallPresenter {
    private val LIVE = setOf(CallPhase.ACTIVE, CallPhase.DIALING, CallPhase.CONNECTING, CallPhase.NEW)

    fun present(calls: List<CallSnapshot>, audio: AudioSnapshot, lastEnded: CallSnapshot?): CallScreenModel {
        calls.firstOrNull { it.phase == CallPhase.SELECT_ACCOUNT }?.let { return CallScreenModel.SelectAccount(it) }
        val ringing = calls.firstOrNull { it.phase == CallPhase.RINGING }
        val live = calls.firstOrNull { it.phase in LIVE }
        val held = calls.firstOrNull { it.phase == CallPhase.HOLDING }
        val current = live ?: held
        return when {
            ringing != null && current != null -> CallScreenModel.Waiting(ringing, current)
            ringing != null -> CallScreenModel.Incoming(ringing)
            live != null -> CallScreenModel.InCall(live, held, audio)
            held != null -> CallScreenModel.InCall(held, null, audio)
            lastEnded != null -> CallScreenModel.Ended(lastEnded)
            else -> CallScreenModel.Idle
        }
    }

    fun elapsedMinutes(call: CallSnapshot, nowMillis: Long): Long? =
        activeSince(call)?.let { (nowMillis - it) / MINUTE_MS }

    fun millisUntilNextMinute(call: CallSnapshot, nowMillis: Long): Long? =
        activeSince(call)?.let { MINUTE_MS - (nowMillis - it).coerceAtLeast(0) % MINUTE_MS }

    private fun activeSince(call: CallSnapshot): Long? =
        call.connectedAtMillis?.takeIf { it > 0 && call.phase == CallPhase.ACTIVE }

    private const val MINUTE_MS = 60_000L
}
