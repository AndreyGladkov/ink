package io.github.andreygladkov.inkgallery.viewer

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import io.github.andreygladkov.ink.eink.A9Panel

/** A screen shown in a refresh mode of its own, or in the person's usual one for null. */
interface ModeScreen {
    fun refreshMode(): A9Panel.RefreshMode?
}

/** Photos and their details are shown in CLEAR unless the person turned that off. */
fun clearIfWanted(context: Context) = A9Panel.RefreshMode.CLEAR.takeIf { ClearMode.isWanted(context) }

/**
 * Keeps the A9 panel in the mode of the mode screen opened last among those on screen, and gives the person's usual
 * mode back once none is: back to the grid, or out of the app. A screen opened over another starts before the one
 * below stops, and one returned to starts again before the one above stops, so the mode switches once per step.
 * A screen being recreated keeps the mode, since every switch costs the panel a long blinking refresh.
 */
object PanelMode : Application.ActivityLifecycleCallbacks {
    private val shown = mutableListOf<Activity>()
    private var held: A9Panel.RefreshMode? = null

    /**
     * A process that died with a mode held left it on the whole phone, since the service keeps it across app
     * switches. Restoring at start fixes that, and costs nothing otherwise: the service ignores a restore with no
     * held mode (see a9/display-hardware.md).
     */
    fun attach(app: Application) {
        shown.clear()
        held = null
        if (A9Panel.isInstalled(app)) A9Panel.restoreMode(app)
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity !is ModeScreen) return
        shown.remove(activity)
        shown += activity
        apply(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity !is ModeScreen) return
        shown.remove(activity)
        if (!activity.isChangingConfigurations) apply(activity)
    }

    private fun apply(activity: Activity) {
        val wanted = (shown.lastOrNull() as ModeScreen?)?.refreshMode()?.takeIf { A9Panel.isInstalled(activity) }
        if (wanted == held) return
        if (wanted == null) A9Panel.restoreMode(activity) else A9Panel.holdMode(activity, wanted)
        held = wanted
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
