package io.github.andreygladkov.inkgallery.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkgallery.FakeMedia
import io.github.andreygladkov.inkgallery.FakeMedia.Row
import io.github.andreygladkov.inkgallery.FakeShared
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver = context.contentResolver

    @Before fun library() {
        FakeMedia.install()
        FakeMedia.rows += Row(1, "old.jpg", "camera", "Camera", takenAt = 1_000)
        FakeMedia.rows += Row(2, "screenshot.png", "shots", "Screenshots", takenAt = null, addedAt = 10)
        FakeMedia.rows += Row(3, "new.jpg", "camera", "Camera", takenAt = 9_000)
        FakeMedia.rows += Row(4, "turned.jpg", "camera", "Camera", takenAt = 2_000, width = 400, height = 300, orientation = 90)
    }

    @Test fun photosComeNewestFirstWithTheTimeAddedStandingInForAMissingCaptureTime() {
        assertEquals(listOf("screenshot.png", "new.jpg", "turned.jpg", "old.jpg"), Library.items(resolver).map { it.name })
        assertEquals(10_000L, Library.items(resolver).first().takenAt)
    }

    @Test fun videosComeAmongThePhotosWithTheirLengthAndTheirOwnAddress() {
        FakeMedia.rows += Row(5, "clip.mp4", "camera", "Camera", takenAt = 5_000, durationMs = 90_000)
        val items = Library.items(resolver)
        assertEquals(listOf("screenshot.png", "new.jpg", "clip.mp4", "turned.jpg", "old.jpg"), items.map { it.name })
        val clip = items.single { it.name == "clip.mp4" }
        assertTrue(clip.isVideo)
        assertEquals(90_000L, clip.durationMs)
        assertEquals(FakeMedia.videoUri(5), clip.uri)
        assertFalse(items.single { it.name == "new.jpg" }.isVideo)
        assertEquals(Album("camera", "Camera", 4), Library.albums(items).last())
        assertEquals(clip, Item.fromBundle(clip.toBundle()))
    }

    @Test fun anItemFromElsewhereIsAVideoByItsType() {
        FakeShared.install(context)
        assertFalse(Library.item(resolver, FakeShared.uri).isVideo)
        FakeShared.type = "video/mp4"
        assertTrue(Library.item(resolver, FakeShared.uri).isVideo)
    }

    @Test fun anAlbumHoldsOnlyItsOwnPhotos() {
        assertEquals(listOf("new.jpg", "turned.jpg", "old.jpg"), Library.items(resolver, "camera").map { it.name })
    }

    @Test fun albumsComeInTheOrderOfTheirNewestPhoto() {
        val albums = Library.albums(Library.items(resolver))
        assertEquals(listOf(Album("shots", "Screenshots", 1), Album("camera", "Camera", 3)), albums)
    }

    @Test fun aTurnedPhotoReportsItsUprightSize() {
        val turned = Library.items(resolver).single { it.name == "turned.jpg" }
        assertEquals(300 to 400, turned.width to turned.height)
        assertEquals("DCIM/Camera", turned.folder)
    }

    @Test fun aLibraryPictureOpenedByAnotherAppIsFoundThereAndKeepsTheAddressItCameWith() {
        val given = android.net.Uri.parse("content://media/external_primary/images/media/3")
        val photo = Library.item(resolver, given)
        assertEquals("new.jpg", photo.name)
        assertEquals(given, photo.uri)
        assertTrue(photo.isInLibrary)
    }

    @Test fun aPictureOutsideAnyFolderIsAmongAllPhotosButInNoAlbum() {
        FakeMedia.rows += Row(5, "loose.jpg", null, null, takenAt = 20_000)
        val photos = Library.items(resolver)
        assertEquals("loose.jpg", photos.first().name)
        assertTrue(photos.first().isInLibrary)
        assertEquals(listOf("shots", "camera"), Library.albums(photos).map { it.id })
    }

    @Test fun aPictureFromElsewhereIsKnownByItsNameAndSizeOnly() {
        FakeShared.install(context)
        val photo = Library.item(resolver, FakeShared.uri)
        assertEquals("shared.png" to 4321L, photo.name to photo.sizeBytes)
        assertFalse(photo.isInLibrary)
    }

    @Test fun aPhotoSurvivesTheTripThroughAnIntent() {
        val photo = Library.items(resolver).first()
        assertEquals(photo, Item.fromBundle(photo.toBundle()))
        val shared = Item(FakeShared.uri, "shared.png")
        assertEquals(shared, Item.fromBundle(shared.toBundle()))
    }
}
