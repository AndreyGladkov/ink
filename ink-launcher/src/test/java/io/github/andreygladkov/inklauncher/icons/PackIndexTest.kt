package io.github.andreygladkov.inklauncher.icons

import io.github.andreygladkov.ink.search.SearchQuery
import io.github.andreygladkov.inklauncher.apps.LauncherApp
import io.github.andreygladkov.inklauncher.home.AppKey
import kotlin.test.Test
import kotlin.test.assertEquals

class PackIndexTest {
    private val telegram = AppKey("org.telegram.messenger", "org.telegram.messenger.DefaultIcon")
    private val avito = AppKey("com.avito.android", "com.avito.android.Launcher")
    private val components = mapOf(
        AppKey("org.telegram.messenger", "org.telegram.messenger.LaunchActivity") to listOf("telegram", "telegram_alt"),
        avito to listOf("avito"),
        AppKey("com.google.android.gm", "com.google.android.gm.ConversationListActivity") to listOf("gmail"),
    )
    private val categories = listOf(PackCategory("Google", listOf("gmail")), PackCategory("A-Z", listOf("avito", "gmail", "telebox", "telegram", "zoom")))
    private val apps = listOf(LauncherApp(avito, "Авито"), LauncherApp(telegram, "Телеграм"))

    private val index = PackIndex.build(components, categories, apps, telegram)

    @Test fun theAppAtHandGetsEveryVariantEvenUnderAnotherActivity() {
        assertEquals(listOf("telegram", "telegram_alt"), index.forApp.map { it.drawable })
    }

    @Test fun installedAppsGetTheirFirstIconUnderTheirOwnName() {
        assertEquals(listOf("avito" to "Авито", "telegram" to "Телеграм"), index.installed.map { it.drawable to it.label })
    }

    @Test fun categoriesKeepThePacksOrderAndOtherIconsTheirDrawableName() {
        assertEquals(listOf("Google", "A-Z"), index.categories.map { it.title })
        assertEquals(listOf("Авито", "gmail", "telebox", "Телеграм", "zoom"), index.categories[1].icons.map { it.label })
        assertEquals(listOf("avito", "gmail", "telebox", "telegram", "telegram_alt", "zoom"), index.all.map { it.drawable })
    }

    @Test fun searchFindsByDrawableNameOrByTheInstalledAppsName() {
        assertEquals(listOf("telegram", "telegram_alt", "telebox"), index.search(SearchQuery("tele")).map { it.drawable })
        assertEquals(listOf("avito"), index.search(SearchQuery("ави")).map { it.drawable })
        assertEquals(listOf("telegram_alt"), index.search(SearchQuery("alt")).map { it.drawable })
        assertEquals(emptyList(), index.search(SearchQuery("")))
    }
}
