package io.github.andreygladkov.inkgallery

import android.Manifest
import android.app.Activity
import android.app.Application
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
import kotlin.test.assertNotNull
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

    @Test fun onlyPhotosAllowedIsPartialAccess() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        val activity = launch()
        idleUntil { activity.cells().isNotEmpty() }
        val texts = contentRoot(activity).find<InkTextView>().filter { it.isShown }.map { it.text.toString() }
        assertTrue("Видны только выбранные вами фото и видео" in texts, "shown: $texts")
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
