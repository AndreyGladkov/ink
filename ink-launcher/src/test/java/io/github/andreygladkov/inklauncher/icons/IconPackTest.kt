package io.github.andreygladkov.inklauncher.icons

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class IconPackTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test fun packsAreFoundByTheirActionOrOnlyByACategory() {
        install("pack.adw", "Lines", IntentFilter("org.adw.launcher.THEMES"))
        install("pack.apex", "Arcs", IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory("com.anddoes.launcher.THEME")
        })
        install("not.a.pack", "Mail", IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        assertEquals(listOf("Arcs", "Lines"), IconPack.installed(context).map { it.label })
    }

    private fun install(pkg: String, label: String, filter: IntentFilter) {
        val pm = shadowOf(context.packageManager)
        pm.installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply {
                packageName = pkg
                nonLocalizedLabel = label
            }
        })
        val component = ComponentName(pkg, "$pkg.Main")
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, filter)
    }
}
