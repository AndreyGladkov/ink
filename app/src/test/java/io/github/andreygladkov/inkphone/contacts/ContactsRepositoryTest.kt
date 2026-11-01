package io.github.andreygladkov.inkphone.contacts

import android.Manifest
import android.app.Application
import android.content.Context
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class ContactsRepositoryTest {
    private val telegram = AccountRef("org.telegram.messenger", "42")
    private lateinit var repository: ContactsRepository

    @Before fun setUp() {
        FakeContactsProvider.reset()
        shadowOf(ApplicationProvider.getApplicationContext<Context>() as Application).grantPermissions(Manifest.permission.READ_CONTACTS)
        Robolectric.setupContentProvider(FakeContactsProvider::class.java, ContactsContract.AUTHORITY)
        repository = ContactsRepository(ApplicationProvider.getApplicationContext<Context>().contentResolver)
    }

    @Test fun summariesCarryNumberDigitsAndAccounts() {
        FakeContactsProvider.addContact(1, "Анна", phones = listOf("+7 (701) 123-45-67"), accounts = listOf(AccountRef("com.google", "a"), telegram))
        FakeContactsProvider.addContact(2, "Борис", alternative = "Борисов, Борис")
        val byId = repository.loadSummaries().associateBy { it.id }
        assertEquals(listOf("77011234567"), byId.getValue(1).numberDigits)
        assertEquals(setOf(AccountRef("com.google", "a"), telegram), byId.getValue(1).accounts)
        assertEquals(true, byId.getValue(1).hasPhone)
        assertEquals(false, byId.getValue(2).hasPhone)
        assertEquals("Борисов, Борис", byId.getValue(2).nameAlternative)
    }

    @Test fun detailsMergeDuplicateNumbersFromLinkedRawContacts() {
        FakeContactsProvider.addContact(
            1, "Анна",
            phones = listOf("+77011234567", "+7 701 123 45 67", "+77020000000"),
            emails = listOf("anna@example.com"),
            defaultPhone = "+7 701 123 45 67",
            accounts = listOf(AccountRef("com.google", "a"), telegram, telegram),
        )
        val details = repository.loadDetails(1)!!
        assertEquals("Анна", details.name)
        assertEquals(listOf("+7 701 123 45 67" to true, "+77020000000" to false), details.phones.map { it.number to it.isDefault })
        assertEquals(listOf("anna@example.com"), details.emails)
        assertEquals(listOf(AccountRef("com.google", "a"), telegram), details.accounts)
    }

    @Test fun missingContactHasNoDetails() {
        assertNull(repository.loadDetails(7))
    }
}
