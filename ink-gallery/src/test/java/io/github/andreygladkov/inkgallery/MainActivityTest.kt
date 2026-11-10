package io.github.andreygladkov.inkgallery

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkgallery.FakeMedia.Row
import io.github.andreygladkov.inkgallery.grid.AlbumActivity
import io.github.andreygladkov.inkgallery.viewer.Actions
import io.github.andreygladkov.inkgallery.viewer.ViewerActivity
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class MainActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @After fun settle() = drainBackground()

    @Before fun library() {
        FakeMedia.install()
        (1L..20L).forEach { id ->
            val album = if (id % 5 == 0L) "shots" to "Screenshots" else "camera" to "Camera"
            FakeMedia.add(context, Row(id, "photo$id.png", album.first, album.second, takenAt = id * 1000))
        }
    }

    private fun launch(): Activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    /** The thumbnails on the shown page, by the photo each opens. */
    private fun Activity.cells() = contentRoot(this).find<View>().filter { it.isClickable && it.contentDescription?.startsWith("photo") == true }
        .filter { it.isShown }

    private fun Activity.grid() = contentRoot(this).find<InkPagedList>().first { it.isShown }

    private fun Activity.tab(label: String) = contentRoot(this).find<InkButton>().single { it.text.toString() == label }

    @Test fun withoutAccessItAsksForIt() {
        val activity = launch()
        val texts = contentRoot(activity).find<InkTextView>().filter { it.isShown }.map { it.text.toString() }
        assertTrue("Чтобы показать фото и видео, Ink Gallery нужен доступ к ним" in texts, "shown: $texts")
        assertTrue("Разрешить доступ" in texts, "shown: $texts")
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun photosComeNewestFirstThreeToARowAPageAtATime() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        val activity = launch()
        idleUntil { activity.cells().isNotEmpty() }
        val firstPage = activity.cells().map { it.contentDescription.toString() }
        assertEquals((20 downTo 20 - firstPage.size + 1).map { "photo$it.png" }, firstPage)
        assertTrue(firstPage.size % 3 == 0 && firstPage.size >= 9, "a page holds ${firstPage.size}")
        val grid = activity.grid()
        assertTrue(grid.pageCount > 1)
        grid.showPage(1)
        assertEquals("photo${20 - firstPage.size}.png", activity.cells().first().contentDescription)
    }

    @Test fun thumbnailsAreDrawnFromTheLibrary() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        val activity = launch()
        idleUntil { activity.cells().isNotEmpty() }
        val cell = activity.cells().first()
        idleUntil { render(cell, cell.width, cell.height).getPixel(cell.width / 2, cell.height / 2) != android.graphics.Color.TRANSPARENT }
    }

    @Test fun theNextPagesThumbnailsAreReadyBeforeItIsTurnedTo() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        val activity = launch()
        idleUntil { activity.cells().isNotEmpty() }
        drainBackground()
        activity.grid().showPage(1)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.cells().isNotEmpty())
        activity.cells().forEach { cell ->
            assertTrue(render(cell, cell.width, cell.height).getPixel(cell.width / 2, cell.height / 2) != android.graphics.Color.TRANSPARENT,
                "${cell.contentDescription} drawn blank")
        }
    }

    @Test fun comingBackToTheSamePhotosRedrawsNothing() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idleUntil { controller.get().cells().isNotEmpty() }
        val shown = controller.get().cells()
        controller.pause().stop().restart().start().resume()
        drainBackground()
        assertTrue(controller.get().cells().zip(shown).all { (now, before) -> now === before })
    }

    @Test fun aThumbnailOpensItsPhotoAmongAll() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        val activity = launch()
        idleUntil { activity.cells().isNotEmpty() }
        activity.cells()[1].performClick()
        val started = assertNotNull(shadowOf(activity).nextStartedActivity)
        assertEquals(ViewerActivity::class.java.name, started.component?.className)
        val viewer = Robolectric.buildActivity(ViewerActivity::class.java, started).setup().get()
        idleUntil { viewer.shownPhoto != null }
        assertEquals("photo19.png", viewer.shownPhoto?.name)
    }

    @Test fun albumsListTheirNameAndCountAndOpenTheirOwnGrid() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        val activity = launch()
        idleUntil { activity.cells().isNotEmpty() }
        activity.tab("Альбомы").performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val rows = activity.grid().find<InkButton>().filter { it.text.contains("\n") }
        assertEquals(listOf("Screenshots\n4 фото", "Camera\n16 фото"), rows.map { it.text.toString() })
        rows.last().performClick()
        val started = assertNotNull(shadowOf(activity).nextStartedActivity)
        val album = Robolectric.buildActivity(AlbumActivity::class.java, started).setup().get()
        idleUntil { album.cells().isNotEmpty() }
        assertEquals("photo19.png", album.cells().first().contentDescription)
        assertTrue(album.cells().none { it.contentDescription == "photo20.png" })
    }

    @Test fun backFromAlbumsReturnsToPhotos() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        val activity = launch()
        idleUntil { activity.cells().isNotEmpty() }
        activity.tab("Альбомы").performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.cells().isEmpty())
        @Suppress("DEPRECATION") activity.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.cells().isNotEmpty())
        assertTrue(!activity.isFinishing)
    }

    @Test fun withSomePhotosPickedItSaysSoAndShowsThem() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        val activity = launch()
        idleUntil { activity.cells().isNotEmpty() }
        val texts = contentRoot(activity).find<InkTextView>().filter { it.isShown }.map { it.text.toString() }
        assertTrue("Видны только выбранные вами фото и видео" in texts, "shown: $texts")
    }

    @Test fun aVideoIsMarkedWithItsLengthAndPlaysAtOnce() {
        FakeMedia.add(context, Row(21, "clip.mp4", "camera", "Camera", takenAt = 21_000, durationMs = 90_000))
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        val activity = launch()
        idleUntil { activity.cells().isNotEmpty() }
        val video = contentRoot(activity).find<View>().first { it.contentDescription?.startsWith("clip.mp4") == true }
        assertEquals("clip.mp4, видео 1:30", video.contentDescription)
        video.performClick()
        val started = assertNotNull(shadowOf(activity).nextStartedActivity)
        assertEquals(io.github.andreygladkov.inkgallery.video.VideoActivity::class.java.name, started.component?.className)
    }

    @Test fun onlyPhotosAllowedIsPartialAccessAndAsksForTheRestAgain() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        val activity = launch()
        idleUntil { activity.cells().isNotEmpty() }
        val texts = contentRoot(activity).find<InkTextView>().filter { it.isShown }.map { it.text.toString() }
        assertTrue("Видны только выбранные вами фото и видео" in texts, "shown: $texts")
        contentRoot(activity).find<InkButton>().single { it.isShown && it.text.toString() == "Разрешить доступ" }.performClick()
        val asked = assertNotNull(shadowOf(activity).lastRequestedPermission).requestedPermissions.toList()
        assertTrue(Manifest.permission.READ_MEDIA_VIDEO in asked, "asked for $asked")
    }

    private fun Activity.longPress(view: View) = view.performLongClick()

    private fun Activity.title() = contentRoot(this).find<InkTextView>().first { it.isShown && it.tag == "ink_header_title" }.text.toString()

    private fun Activity.barButton(name: String) =
        contentRoot(this).find<InkButton>().single { it.isShown && (it.contentDescription == name || it.text.toString() == name) }

    @Test fun aLongPressStartsPickingAndTapsPickMoreOnAnyPage() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        val activity = launch()
        idleUntil { activity.cells().isNotEmpty() }
        val firstPage = activity.cells()
        activity.longPress(activity.cells()[0])
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Выбрано: 1", activity.title())
        assertTrue(activity.cells()[0].isSelected)
        assertTrue(activity.cells().zip(firstPage).all { (now, before) -> now === before }, "the page was laid out again")
        assertFalse(contentRoot(activity).find<InkButton>().any { it.isShown && it.text.toString() == "Альбомы" }, "the tabs stay while picking")
        activity.cells()[1].performClick()
        assertEquals("Выбрано: 2", activity.title())
        assertNull(shadowOf(activity).nextStartedActivity, "a tap while picking opened a photo")
        activity.grid().showPage(1)
        shadowOf(Looper.getMainLooper()).idle()
        activity.cells()[0].performClick()
        assertEquals("Выбрано: 3", activity.title())
        activity.grid().showPage(0)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.cells()[1].isSelected)
        activity.cells()[1].performClick()
        assertEquals("Выбрано: 2", activity.title())
    }

    @Test fun cancelOrBackEndsPicking() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        val activity = launch()
        idleUntil { activity.cells().isNotEmpty() }
        activity.longPress(activity.cells()[0])
        activity.barButton("Отмена").performClick()
        assertEquals("Фото", activity.title())
        assertFalse(activity.cells()[0].isSelected)
        activity.longPress(activity.cells()[0])
        @Suppress("DEPRECATION") activity.onBackPressed()
        assertEquals("Фото", activity.title())
        assertFalse(activity.isFinishing)
    }

    @Test fun thePickedAreSharedAndDeletedTogether() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        val activity = launch()
        idleUntil { activity.cells().isNotEmpty() }
        activity.longPress(activity.cells()[0])
        activity.cells()[2].performClick()
        activity.barButton("Поделиться").performClick()
        val send = androidx.core.content.IntentCompat.getParcelableExtra(assertNotNull(shadowOf(activity).nextStartedActivity), Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND_MULTIPLE, send.action)
        assertEquals<List<android.net.Uri>?>(listOf(FakeMedia.uri(20), FakeMedia.uri(18)),
            androidx.core.content.IntentCompat.getParcelableArrayListExtra(send, Intent.EXTRA_STREAM, android.net.Uri::class.java))

        activity.barButton("Удалить").performClick()
        assertTrue(contentRoot(activity).find<InkTextView>().any { it.isShown && it.text.toString() == "Удалить выбранные: 2?" })
        assertTrue(FakeMedia.deleteRequests.isEmpty())
        activity.barButton("OK").performClick()
        assertEquals(listOf(FakeMedia.uri(20), FakeMedia.uri(18)), FakeMedia.deleteRequests)
        FakeMedia.rows.removeAll { it.id == 20L || it.id == 18L }
        activity.deliverResult(Actions.REQUEST_DELETE, Activity.RESULT_OK)
        assertEquals("Фото", activity.title())
        idleUntil { activity.cells().first().contentDescription == "photo19.png" }
        assertTrue(activity.cells().none { it.contentDescription == "photo18.png" })
    }

    @Test fun anAlbumPicksAndDeletesToo() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        val album = Robolectric.buildActivity(AlbumActivity::class.java, AlbumActivity.intent(context, io.github.andreygladkov.inkgallery.media.Album("shots", "Screenshots", 4))).setup().get()
        idleUntil { album.cells().isNotEmpty() }
        album.longPress(album.cells()[0])
        assertEquals("Выбрано: 1", album.title())
        album.barButton("Удалить").performClick()
        album.barButton("OK").performClick()
        assertEquals(listOf(FakeMedia.uri(20)), FakeMedia.deleteRequests)
    }

    @Test fun anEmptyLibrarySaysSo() {
        FakeMedia.rows.clear()
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        val activity = launch()
        idleUntil { contentRoot(activity).find<InkTextView>().any { it.isShown && it.text.toString() == "Фото и видео пока нет" } }
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun newPhotosAppearWhenTheAppComesBack() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idleUntil { controller.get().cells().isNotEmpty() }
        FakeMedia.add(context, Row(21, "photo21.png", "shots", "Screenshots", takenAt = 21_000))
        controller.pause().stop().restart().start().resume()
        idleUntil { controller.get().cells().first().contentDescription == "photo21.png" }
    }
}
