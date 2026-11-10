package io.github.andreygladkov.inkgallery.viewer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkgallery.A9
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class PanelModeTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test fun aStartingAppGivesBackAModeLeftHeldByAProcessThatDied() {
        A9.installService(context)
        PanelMode.attach(context)
        assertEquals(listOf("RESTORE"), A9.modeChanges(context))
    }

    @Test fun withoutTheServiceNothingIsSentAtStart() {
        PanelMode.attach(context)
        assertEquals(emptyList(), A9.modeChanges(context))
    }
}
