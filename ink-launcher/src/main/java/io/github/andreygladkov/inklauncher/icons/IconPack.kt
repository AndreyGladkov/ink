package io.github.andreygladkov.inklauncher.icons

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import androidx.core.net.toUri
import io.github.andreygladkov.inklauncher.home.AppKey
import org.xmlpull.v1.XmlPullParser
import java.text.Collator

data class IconPackInfo(val packageName: String, val label: String)

/**
 * An ADW/Nova-style pack: a package whose appfilter.xml maps launcher activities to its drawables, and whose
 * drawable.xml, when present, sorts its drawables into the pack's own categories. Both files can be megabytes
 * long, so they are read on first use, off the main thread where the caller can.
 */
class IconPack private constructor(val packageName: String, private val resources: Resources) {
    private val parsedComponents = lazy { readXml(APP_FILTER, AppFilter::parse).orEmpty() }

    val components: Map<AppKey, List<String>> by parsedComponents

    val isParsed: Boolean get() = parsedComponents.isInitialized()

    val categories: List<PackCategory> by lazy { readXml(DRAWABLES, DrawableXml::parse).orEmpty() }

    fun drawableFor(key: AppKey): String? = components[key]?.firstOrNull { drawableId(it) != 0 }

    fun drawable(name: String): Drawable? = drawableId(name).takeIf { it != 0 }?.let { id ->
        runCatching { resources.getDrawable(id, null) }.getOrNull()
    }

    // Packs are other apps: their resource names are only known at run time.
    @SuppressLint("DiscouragedApi")
    private fun drawableId(name: String) = resources.getIdentifier(name, "drawable", packageName)

    @SuppressLint("DiscouragedApi")
    private fun <T> readXml(name: String, parse: (XmlPullParser) -> T): T? = runCatching {
        val xmlId = resources.getIdentifier(name, "xml", packageName)
        if (xmlId != 0) {
            resources.getXml(xmlId).use(parse)
        } else {
            resources.assets.open("$name.xml").use { stream -> parse(Xml.newPullParser().apply { setInput(stream, null) }) }
        }
    }.getOrNull()

    companion object {
        private const val APP_FILTER = "appfilter"
        private const val DRAWABLES = "drawable"
        private val ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME",
        )

        // Many packs declare themselves only by a category on their main activity.
        private val CATEGORIES = listOf(
            "com.anddoes.launcher.THEME",
            "com.fede.launcher.THEME_ICONPACK",
            "com.teslacoilsw.launcher.THEME",
        )

        private val loaded = HashMap<String, IconPack>()

        fun installed(context: Context): List<IconPackInfo> {
            val pm = context.packageManager
            val collator = Collator.getInstance(context.resources.configuration.locales[0])
            val intents = ACTIONS.map(::Intent) + CATEGORIES.map { Intent(Intent.ACTION_MAIN).addCategory(it) }
            return intents.flatMap { pm.queryIntentActivities(it, 0) }
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .map { IconPackInfo(it.packageName, it.loadLabel(pm).toString()) }
                .sortedWith(compareBy(collator) { it.label })
        }

        fun load(context: Context, packageName: String): IconPack? = synchronized(loaded) {
            loaded[packageName] ?: runCatching {
                IconPack(packageName, context.packageManager.getResourcesForApplication(packageName))
            }.getOrNull()?.also { loaded[packageName] = it }
        }

        fun search(context: Context) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, "market://search?q=icon%20pack&c=apps".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        fun forget(packageName: String) = synchronized(loaded) { loaded.remove(packageName) }
    }
}

data class PackCategory(val title: String, val drawables: List<String>)

object AppFilter {
    private const val PREFIX = "ComponentInfo{"

    /** Packs often give one activity several drawables; the first is the one meant by default. */
    fun parse(parser: XmlPullParser): Map<AppKey, List<String>> {
        val result = LinkedHashMap<AppKey, MutableList<String>>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
            val component = parser.getAttributeValue(null, "component") ?: continue
            val drawable = parser.getAttributeValue(null, "drawable")?.takeIf { it.isNotBlank() } ?: continue
            val key = AppKey.unflatten(component.removePrefix(PREFIX).removeSuffix("}")) ?: continue
            val drawables = result.getOrPut(key) { mutableListOf() }
            if (drawable !in drawables) drawables += drawable
        }
        return result
    }
}

/** Items follow the category they belong to; items before the first category have none and are left out. */
object DrawableXml {
    fun parse(parser: XmlPullParser): List<PackCategory> {
        val result = mutableListOf<PackCategory>()
        var title: String? = null
        val items = mutableListOf<String>()
        fun close() {
            val current = title ?: return
            if (items.isNotEmpty()) result += PackCategory(current, items.distinct())
            items.clear()
        }
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "category" -> {
                    close()
                    title = parser.getAttributeValue(null, "title")
                }
                "item" -> if (title != null) parser.getAttributeValue(null, "drawable")?.takeIf { it.isNotBlank() }?.let(items::add)
            }
        }
        close()
        return result
    }
}
