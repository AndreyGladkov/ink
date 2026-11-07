package io.github.andreygladkov.inklauncher.icons

import io.github.andreygladkov.ink.search.SearchQuery
import io.github.andreygladkov.inklauncher.apps.LauncherApp
import io.github.andreygladkov.inklauncher.home.AppKey

/** [label] is the name of the installed app the icon is drawn for, or else the drawable's own name. */
data class PackIcon(val drawable: String, val label: String, val tokens: List<String>, val isInstalled: Boolean = false)

data class PackGroup(val title: String, val icons: List<PackIcon>)

/** A pack's drawables arranged for picking one: what it draws for the app at hand, for the apps installed, by its categories. */
class PackIndex(
    val forApp: List<PackIcon>,
    val installed: List<PackIcon>,
    val categories: List<PackGroup>,
    val all: List<PackIcon>,
) {
    /** Icons drawn for apps on this phone come first; the rest keep the pack's alphabetical order. */
    fun search(query: SearchQuery): List<PackIcon> =
        if (query.isEmpty) emptyList() else all.filter { query.matches(it.tokens) }.sortedBy { !it.isInstalled }

    companion object {
        fun build(
            components: Map<AppKey, List<String>>,
            categories: List<PackCategory>,
            apps: List<LauncherApp>,
            app: AppKey?,
        ): PackIndex {
            val byPackage = components.entries.groupBy({ it.key.packageName }, { it.value })
            fun drawablesOf(key: AppKey) = components[key] ?: byPackage[key.packageName]?.flatten().orEmpty()

            val appLabels = HashMap<String, MutableList<String>>()
            apps.forEach { installed -> drawablesOf(installed.key).forEach { appLabels.getOrPut(it) { mutableListOf() } += installed.label } }

            fun icon(drawable: String): PackIcon {
                val name = drawable.replace('_', ' ').trim()
                val labels = appLabels[drawable].orEmpty()
                return PackIcon(drawable, labels.firstOrNull() ?: name, SearchQuery.tokens(name) + labels.flatMap(SearchQuery::tokens), labels.isNotEmpty())
            }

            val all = (components.values.flatten() + categories.flatMap { it.drawables }).distinct().sorted()
            return PackIndex(
                forApp = app?.let(::drawablesOf).orEmpty().distinct().map(::icon),
                installed = apps.mapNotNull { drawablesOf(it.key).firstOrNull() }.distinct().map(::icon),
                categories = categories.map { category -> PackGroup(category.title, category.drawables.map(::icon)) },
                all = all.map(::icon),
            )
        }
    }
}
