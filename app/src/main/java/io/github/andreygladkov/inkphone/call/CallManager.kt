package io.github.andreygladkov.inkphone.call

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import android.telecom.VideoProfile

// No locking: Telecom delivers InCallService callbacks on the main thread and the UI calls in from it as well.
object CallManager {
    private val calls = LinkedHashMap<Call, String>()
    private val names = HashMap<String, String>()
    private val listeners = LinkedHashSet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var service: InCallService? = null
    private var nextId = 0
    private var lastEnded: CallSnapshot? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = changed(call)
        override fun onDetailsChanged(call: Call, details: Call.Details) = changed(call)
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = notifyListeners()
    }

    var nameResolver: (String) -> String? = { null }

    val hasCalls: Boolean get() = calls.isNotEmpty()

    fun attach(service: InCallService) {
        this.service = service
    }

    fun detach(service: InCallService) {
        if (this.service === service) this.service = null
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun onCallAdded(call: Call) {
        calls[call] = "call${nextId++}"
        lastEnded = null
        call.registerCallback(callback)
        resolveName(call)
        notifyListeners()
    }

    fun onCallRemoved(call: Call) {
        lastEnded = snapshot(call).copy(phase = CallPhase.DISCONNECTED)
        call.unregisterCallback(callback)
        calls.remove(call)
        // Names are looked up again for the next calls, so a number saved in between gets its name.
        if (calls.isEmpty()) names.clear()
        notifyListeners()
    }

    fun onAudioChanged() = notifyListeners()

    fun model(): CallScreenModel = CallPresenter.present(snapshots(), audio(), lastEnded)

    fun clearEnded() {
        lastEnded = null
    }

    fun snapshots(): List<CallSnapshot> = calls.keys.map(::snapshot)

    @Suppress("DEPRECATION")
    fun audio(): AudioSnapshot {
        val state = service?.callAudioState ?: return AudioSnapshot()
        val supported = buildSet {
            if (state.supportedRouteMask and CallAudioState.ROUTE_EARPIECE != 0) add(AudioRoute.EARPIECE)
            if (state.supportedRouteMask and CallAudioState.ROUTE_SPEAKER != 0) add(AudioRoute.SPEAKER)
            if (state.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0) add(AudioRoute.BLUETOOTH)
            if (state.supportedRouteMask and CallAudioState.ROUTE_WIRED_HEADSET != 0) add(AudioRoute.WIRED_HEADSET)
        }
        return AudioSnapshot(state.isMuted, routeOf(state.route), supported)
    }

    fun answer(id: String) = find(id)?.answer(VideoProfile.STATE_AUDIO_ONLY)

    fun reject(id: String) = find(id)?.reject(false, null)

    fun hangUp(id: String) = find(id)?.disconnect()

    fun endAndAnswer(incomingId: String, currentId: String) {
        find(currentId)?.disconnect()
        find(incomingId)?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun toggleHold(id: String) {
        val call = find(id) ?: return
        if (phaseOf(call) == CallPhase.HOLDING) call.unhold() else call.hold()
    }

    fun swap(heldId: String) = find(heldId)?.unhold()

    fun merge(primaryId: String, heldId: String) {
        val primary = find(primaryId) ?: return
        val held = find(heldId)
        if (primary.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) primary.mergeConference()
        else if (held != null && held in primary.conferenceableCalls) primary.conference(held)
    }

    fun setMuted(muted: Boolean) = service?.setMuted(muted)

    @Suppress("DEPRECATION")
    fun setRoute(route: AudioRoute) = service?.setAudioRoute(
        when (route) {
            AudioRoute.EARPIECE -> CallAudioState.ROUTE_EARPIECE
            AudioRoute.SPEAKER -> CallAudioState.ROUTE_SPEAKER
            AudioRoute.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            AudioRoute.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
        },
    )

    fun playDtmf(id: String, digit: Char) = find(id)?.playDtmfTone(digit)

    fun stopDtmf(id: String) = find(id)?.stopDtmfTone()

    fun selectAccount(id: String, account: PhoneAccountHandle) = find(id)?.phoneAccountSelected(account, false)

    private fun find(id: String): Call? = calls.entries.firstOrNull { it.value == id }?.key

    private fun changed(call: Call) {
        if (call in calls) notifyListeners()
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it() }
    }

    private fun resolveName(call: Call) {
        val number = numberOf(call) ?: return
        if (number in names) return
        Thread {
            val name = runCatching { nameResolver(number) }.getOrNull() ?: return@Thread
            main.post {
                names[number] = name
                notifyListeners()
            }
        }.start()
    }

    private fun snapshot(call: Call): CallSnapshot {
        val details = call.details
        val number = numberOf(call)
        return CallSnapshot(
            id = calls[call] ?: "",
            number = number,
            name = number?.let { names[it] } ?: details.callerDisplayName?.takeIf { it.isNotBlank() },
            phase = phaseOf(call),
            connectedAtMillis = details.connectTimeMillis.takeIf { it > 0 },
            canHold = details.can(Call.Details.CAPABILITY_HOLD) || details.can(Call.Details.CAPABILITY_SUPPORT_HOLD),
            canMerge = details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) || call.conferenceableCalls.isNotEmpty(),
            isConference = details.hasProperty(Call.Details.PROPERTY_CONFERENCE),
        )
    }

    private fun numberOf(call: Call): String? = call.details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }

    @Suppress("DEPRECATION")
    private fun phaseOf(call: Call): CallPhase {
        val state = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) call.details.state else call.state
        return when (state) {
            Call.STATE_NEW -> CallPhase.NEW
            Call.STATE_RINGING -> CallPhase.RINGING
            Call.STATE_DIALING -> CallPhase.DIALING
            Call.STATE_CONNECTING, Call.STATE_PULLING_CALL -> CallPhase.CONNECTING
            Call.STATE_SELECT_PHONE_ACCOUNT -> CallPhase.SELECT_ACCOUNT
            Call.STATE_ACTIVE -> CallPhase.ACTIVE
            Call.STATE_HOLDING -> CallPhase.HOLDING
            Call.STATE_DISCONNECTING -> CallPhase.DISCONNECTING
            else -> CallPhase.DISCONNECTED
        }
    }

    private fun routeOf(route: Int): AudioRoute = when (route) {
        CallAudioState.ROUTE_SPEAKER -> AudioRoute.SPEAKER
        CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
        CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
        else -> AudioRoute.EARPIECE
    }
}
