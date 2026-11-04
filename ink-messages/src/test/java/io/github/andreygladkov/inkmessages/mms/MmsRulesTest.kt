package io.github.andreygladkov.inkmessages.mms

import io.github.andreygladkov.inkmessages.data.Attachment
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class MmsRulesTest {
    @Test fun aSingleRecipientIsThisPhoneEvenWithoutKnowingItsNumber() {
        assertEquals(listOf(SENDER), MmsStore.threadRecipients(SENDER, listOf(ME), selfNumbers = emptyList()))
    }

    @Test fun aGroupKeepsEveryoneButThisPhoneWhenItsNumberIsKnown() {
        assertEquals(listOf(SENDER, OTHER), MmsStore.threadRecipients(SENDER, listOf("8 900 000-00-01", OTHER), selfNumbers = listOf(ME)))
    }

    @Test fun aGroupKeepsEveryRecipientWhenThisPhonesNumberIsUnknown() {
        assertEquals(listOf(SENDER, ME, OTHER), MmsStore.threadRecipients(SENDER, listOf(ME, OTHER, SENDER), selfNumbers = emptyList()))
    }

    @Test fun pduAddressesLoseTheirTypeSuffix() {
        assertEquals(SENDER, MmsStore.address("$SENDER/TYPE=PLMN"))
    }

    @Test fun attachmentFileNamesAreSafeAndCarryAnExtension() {
        assertEquals("cat.jpg", Attachments.fileName(Attachment(1, "image/jpeg", "cat.jpg")))
        assertEquals("photo.png", Attachments.fileName(Attachment(2, "image/png", "photo")))
        assertEquals("mms-3.vcf", Attachments.fileName(Attachment(3, "text/x-vcard")))
        assertEquals("evil.vcf", Attachments.fileName(Attachment(4, "text/x-vcard", "../../evil.vcf")))
        assertEquals("Иван Петров.vcf", Attachments.fileName(Attachment(5, "text/x-vcard", "Иван Петров.vcf")))
    }

    private companion object {
        const val SENDER = "+79001112233"
        const val ME = "+79000000001"
        const val OTHER = "+79005556677"
    }
}
