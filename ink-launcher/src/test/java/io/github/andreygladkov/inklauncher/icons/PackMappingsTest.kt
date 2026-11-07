package io.github.andreygladkov.inklauncher.icons

import io.github.andreygladkov.inklauncher.home.AppKey
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PackMappingsTest {
    @get:Rule val folder = TemporaryFolder()

    private val mail = AppKey("com.mail", "com.mail.Inbox")
    private val chat = AppKey("com.chat", "com.chat.Main")

    @Test fun aMappingIsReadBackOnlyForTheSamePackVersion() {
        val mappings = PackMappings(folder.root)
        mappings.write("pack", 1, mapOf(mail to "mail", chat to ""))
        assertEquals(mapOf(mail to "mail", chat to ""), mappings.read("pack", 1))
        assertNull(mappings.read("pack", 2))
        assertNull(mappings.read("other", 1))
    }

    @Test fun aNewVersionReplacesTheOldFile() {
        val mappings = PackMappings(folder.root)
        mappings.write("pack", 1, mapOf(mail to "mail"))
        mappings.write("pack", 2, mapOf(mail to "mail_new"))
        assertNull(mappings.read("pack", 1))
        assertEquals(mapOf(mail to "mail_new"), mappings.read("pack", 2))
        assertEquals(1, folder.root.listFiles()!!.size)
    }
}
