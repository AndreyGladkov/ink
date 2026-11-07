package io.github.andreygladkov.inklauncher.icons

import android.util.Xml
import io.github.andreygladkov.inklauncher.home.AppKey
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AppFilterTest {
    @Test fun mapsComponentsToDrawablesSkippingBrokenItems() {
        val xml = """
            <resources>
                <iconback img1="back" />
                <item component="ComponentInfo{com.mail/com.mail.Inbox}" drawable="mail" />
                <item component="ComponentInfo{com.chat/.Main}" drawable="chat" />
                <item component="ComponentInfo{com.mail/com.mail.Inbox}" drawable="mail_alt" />
                <item component=":LAUNCHER_ACTION_APP_DRAWER" drawable="drawer" />
                <item component="ComponentInfo{com.empty/com.empty.Main}" drawable="" />
            </resources>
        """.trimIndent()
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        assertEquals(
            mapOf(AppKey("com.mail", "com.mail.Inbox") to listOf("mail", "mail_alt"), AppKey("com.chat", "com.chat.Main") to listOf("chat")),
            AppFilter.parse(parser),
        )
    }

    @Test fun drawablesFallIntoTheCategoryBeforeThem() {
        val xml = """
            <resources>
                <version>1</version>
                <item drawable="orphan" />
                <category title="Google" />
                <item drawable="gmail" />
                <item drawable="maps" />
                <item drawable="gmail" />
                <category title="Empty" />
                <category title="A-Z" />
                <item drawable="avito" />
            </resources>
        """.trimIndent()
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        assertEquals(
            listOf(PackCategory("Google", listOf("gmail", "maps")), PackCategory("A-Z", listOf("avito"))),
            DrawableXml.parse(parser),
        )
    }
}
