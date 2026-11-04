package io.github.andreygladkov.inkmessages.thread

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.telephony.SmsManager
import android.widget.ImageView
import androidx.core.graphics.createBitmap
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.pixels
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkmessages.TestData
import io.github.andreygladkov.inkmessages.TestData.MMS_INBOX
import io.github.andreygladkov.inkmessages.TestData.context
import io.github.andreygladkov.inkmessages.data.FakeTelephonyProvider
import io.github.andreygladkov.inkmessages.data.TelephonyStore
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class MmsThreadTest {
    private val now = System.currentTimeMillis()
    private var thread = 0L

    @Before fun setUp() {
        TestData.installProviders()
        thread = FakeTelephonyProvider.thread("+79001112233")
    }

    @Test fun aPictureShowsAsAPureBlackAndWhiteThumbnailThatOpensTheViewer() {
        FakeTelephonyProvider.mms(thread, now / 1000, MMS_INBOX, text = "Смотри", imageBytes = png(photo(300, 200)))
        val activity = open()
        val image = contentRoot(activity).find<MmsImageView>().single()
        idleUntil { render(contentRoot(activity)).let { bitmap -> inside(image, bitmap).any { it == Color.BLACK } && inside(image, bitmap).any { it == Color.WHITE } } }
        assertTrue(image.width > image.height, "a landscape picture keeps its proportions: ${image.width}x${image.height}")
        assertPureBlackAndWhite(render(contentRoot(activity)))

        image.performClick()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ImageViewerActivity::class.java.name, started.component!!.className)
    }

    @Test fun aContactOpensInAnAppThatReadsVcards() {
        val vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:Иван\nTEL:+79005556677\nEND:VCARD\n".toByteArray()
        FakeTelephonyProvider.mms(thread, now / 1000, MMS_INBOX, extraParts = listOf("text/x-vcard" to vcard))
        val activity = open()
        contentRoot(activity).find<InkButton>().single { it.text.toString() == "Контакт: Иван" }.performClick()
        idleUntil { shadowOf(activity).peekNextStartedActivity() != null }
        val view = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals("text/x-vcard", view.type)
        assertEquals("${context.packageName}.mmsfiles", view.data!!.authority)
        assertTrue(view.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(String(vcard), context.contentResolver.openInputStream(view.data!!)!!.use { it.reader().readText() })
    }

    @Test fun aMessageThatFailedToDownloadCanBeDownloadedAgain() {
        TestData.makeDefaultSmsApp()
        val id = FakeTelephonyProvider.pendingMms(thread, now / 1000, 10_000, failed = true)
        val activity = open()
        assertTrue(contentRoot(activity).find<InkTextView>().any { it.text.toString().startsWith("MMS · ") })
        val download = contentRoot(activity).find<InkButton>().single { it.text.toString() == "Не загружено — загрузить" }
        download.performClick()
        val sms = shadowOf(context.getSystemService(SmsManager::class.java))
        idleUntil { sms.lastDownloadedMultimediaMessageParams != null }
        assertEquals("http://mmsc.example/$id", sms.lastDownloadedMultimediaMessageParams.locationUrl)
    }

    @Test fun withoutTheSmsRoleAMessageCannotBeDownloaded() {
        FakeTelephonyProvider.pendingMms(thread, now / 1000, 10_000)
        val download = contentRoot(open()).find<InkButton>().single { it.text.toString() == "Загрузить" }
        assertTrue(!download.isEnabled)
    }

    @Test fun theViewerShowsThePictureAsItIsAndSharesIt() {
        val bytes = png(photo(120, 80))
        FakeTelephonyProvider.mms(thread, now / 1000, MMS_INBOX, imageBytes = bytes)
        val image = TelephonyStore(context.contentResolver).thread(thread).single().attachments.single()
        val viewer = Robolectric.buildActivity(ImageViewerActivity::class.java, ImageViewerActivity.intent(context, image)).setup().get()
        idleUntil { contentRoot(viewer).find<ImageView>().single().drawable != null }

        contentRoot(viewer).find<InkButton>().single { it.text.toString() == "Поделиться" }.performClick()
        idleUntil { shadowOf(viewer).peekNextStartedActivity() != null }
        val chooser = shadowOf(viewer).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        val stream = send.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)!!
        assertEquals("image/jpeg", send.type)
        assertTrue(bytes.contentEquals(context.contentResolver.openInputStream(stream)!!.use { it.readBytes() }))
    }

    private fun open(): ThreadActivity {
        val activity = Robolectric.buildActivity(ThreadActivity::class.java, ThreadActivity.intent(context, thread)).setup().get()
        idleUntil { contentRoot(activity).find<BubbleRow>().isNotEmpty() }
        return activity
    }

    private fun inside(view: android.view.View, bitmap: Bitmap): List<Int> {
        val location = IntArray(2).also(view::getLocationInWindow)
        val margin = 4
        val width = view.width - 2 * margin
        val pixels = bitmap.pixels()
        return List(width * (view.height - 2 * margin)) { i -> pixels[(location[1] + margin + i / width) * bitmap.width + location[0] + margin + i % width] }
    }

    private fun photo(width: Int, height: Int) = createBitmap(width, height).apply {
        for (x in 0 until width) for (y in 0 until height) setPixel(x, y, Color.rgb(x * 255 / width, y * 255 / height, 128))
    }

    private fun png(bitmap: Bitmap) = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
