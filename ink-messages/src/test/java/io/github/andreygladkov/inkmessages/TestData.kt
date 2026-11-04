package io.github.andreygladkov.inkmessages

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import android.provider.Telephony.Mms
import android.provider.Telephony.TextBasedSmsColumns
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkmessages.data.FakeBlockedNumberProvider
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
        FakeBlockedNumberProvider.reset()
        listOf("sms", "mms", "mms-sms").forEach { Robolectric.setupContentProvider(FakeTelephonyProvider::class.java, it) }
        Robolectric.setupContentProvider(FakePhoneLookupProvider::class.java, ContactsContract.AUTHORITY)
        Robolectric.setupContentProvider(FakeBlockedNumberProvider::class.java, BlockedNumberContract.AUTHORITY)
        if (grant) shadowOf(context as Application).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS)
    }

    fun makeDefaultSmsApp() {
        shadowOf(context.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_SMS)
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING, true)
        shadowOf(context as Application).grantPermissions(Manifest.permission.SEND_SMS, Manifest.permission.POST_NOTIFICATIONS)
    }
}
