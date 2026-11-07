package io.github.andreygladkov.inklauncher.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class LayoutStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = LayoutStore(context)

    @Test fun anUnreadableLayoutIsKeptAsideAndNotOverwritten() {
        assertNull(store.load())
        File(context.filesDir, "home.json").writeText("{\"pages\": 0}")
        assertNull(store.load())
        val saved = HomeLayout().place(AppTile(1, AppKey("a", "a.A")), Spot(Sheet.Page(0), 0, 0))
        store.save(saved)
        assertEquals(saved, store.load())
        assertEquals("{\"pages\": 0}", File(context.filesDir, "home.broken.json").readText())
    }
}
