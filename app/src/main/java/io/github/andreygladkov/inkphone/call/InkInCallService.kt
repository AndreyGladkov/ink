package io.github.andreygladkov.inkphone.call

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

class InkInCallService : InCallService() {
    private val listener: () -> Unit = { CallNotifications.update(this) }

    override fun onCreate() {
        super.onCreate()
        CallNotifications.createChannels(this)
        CallManager.attach(this)
        val app = applicationContext
        CallManager.nameResolver = { number -> ContactLookup.find(app, number)?.name }
        CallManager.addListener(listener)
    }

    override fun onDestroy() {
        CallManager.removeListener(listener)
        CallManager.detach(this)
        CallNotifications.update(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        CallManager.onCallAdded(call)
        startActivity(CallActivity.intent(this))
    }

    override fun onCallRemoved(call: Call) {
        CallManager.onCallRemoved(call)
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallManager.onAudioChanged()
    }
}
