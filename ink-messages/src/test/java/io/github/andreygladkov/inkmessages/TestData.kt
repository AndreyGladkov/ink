package io.github.andreygladkov.inkmessages

import android.Manifest
import android.app.Application
import android.content.Context
import android.provider.ContactsContract
import android.provider.Telephony.Mms
import android.provider.Telephony.TextBasedSmsColumns
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkmessages.data.FakePhoneLookupProvider
import io.github.andreygladkov.inkmessages.data.FakeTelephonyProvider
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

object TestData {
    const val INBOX = TextBasedSmsColumns.MESSAGE_TYPE_INBOX
    const val SENT = TextBasedSmsColumns.MESSAGE_TYPE_SENT
    const val MMS_INBOX = Mms.MESSAGE_BOX_INBOX

    val context: Context get() = ApplicationProvider.getApplicationContext()

    fun installProviders(grant: Boolean = true) {
        FakeTelephonyProvider.reset()
        FakePhoneLookupProvider.reset()
        listOf("sms", "mms", "mms-sms").forEach { Robolectric.setupContentProvider(FakeTelephonyProvider::class.java, it) }
        Robolectric.setupContentProvider(FakePhoneLookupProvider::class.java, ContactsContract.AUTHORITY)
        if (grant) shadowOf(context as Application).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS)
    }
}
