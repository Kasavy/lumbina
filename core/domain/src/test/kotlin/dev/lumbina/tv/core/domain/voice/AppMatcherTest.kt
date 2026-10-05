package dev.lumbina.tv.core.domain.voice

import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.home.HomeLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppMatcherTest {

    private fun app(label: String, pkg: String, tv: Boolean = true) = App(AppId(pkg, "$pkg.Main"), label, tv)

    private val vk = app("VK Video", "com.vk.tv")
    private val kinopoisk = app("Кинопоиск", "ru.kinopoisk.tv")
    private val smartTube = app("SmartTube", "org.smarttube.stable")
    private val rutube = app("RUTUBE", "ru.rutube.app.tv")
    private val ruStore = app("RuStore", "ru.vk.store.tv")
    private val lampaS = app("LampaS", "top.rootu.lamps")
    private val settings = app("Settings", "com.android.tv.settings")
    private val aptoide = app("Aptoide TV", "cm.aptoidetv.pt")
    private val apps = listOf(vk, kinopoisk, smartTube, rutube, ruStore, lampaS, settings, aptoide)

    private val matcher = AppMatcher(
        aliases = mapOf(
            "youtube" to listOf("smarttube"),
            "ютуб" to listOf("smarttube"),
            "настройки" to listOf("settings"),
        ),
    )

    private fun found(query: String) = (matcher.match(query, apps) as? AppMatch.Found)?.app

    @Test
    fun `a label beats a package name that matches as well`() {
        assertEquals(rutube, found("руту"))
    }

    @Test
    fun `an app renamed in Lumbina answers to both names, once`() {
        val media = app("媒体中心", "com.hisense.media")
        val named = HomeLayout().rename(media.id, "Player").spokenNames(apps + media)

        assertEquals(media.id, (matcher.match("плеер", named) as? AppMatch.Found)?.app?.id)
        assertEquals(media.id, (matcher.match("媒体中心", named) as? AppMatch.Found)?.app?.id)
    }

    @Test
    fun `an app answers to its voice name, and still to the one on screen`() {
        val layout = HomeLayout().voiceName(smartTube.id, "ютуб")
        val named = layout.spokenNames(apps)
        assertEquals(smartTube.id, (AppMatcher().match("ютуб", named) as AppMatch.Found).app.id)
        assertEquals(smartTube.id, (AppMatcher().match("смарт тьюб", named) as AppMatch.Found).app.id)
        assertEquals(apps, layout.voiceName(smartTube.id, " ").spokenNames(apps))
    }

    @Test
    fun `finds apps by the names recognizers write`() {
        val expected = mapOf(
            "кинопоиск" to kinopoisk,
            "kinopoisk" to kinopoisk,
            "вк видео" to vk,
            "vk video" to vk,
            "вк" to vk,
            "смарт тьюб" to smartTube,
            "смарттюб" to smartTube,
            "ютуб" to smartTube,
            "youtube" to smartTube,
            "рутуб" to rutube,
            "рутьюб" to rutube,
            "ру стор" to ruStore,
            "лампа" to lampaS,
            "лампас" to lampaS,
            "настройки" to settings,
            "settings" to settings,
            "аптоид" to aptoide,
        )
        val wrong = expected.mapNotNull { (query, app) ->
            val actual = found(query)
            "\"$query\": expected ${app.label}, got ${actual?.label ?: matcher.match(query, apps)}"
                .takeIf { actual != app }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun `does not guess on unrelated names`() {
        assertEquals(AppMatch.None, matcher.match("телеграм", apps))
        assertEquals(AppMatch.None, matcher.match("x", apps))
    }

    @Test
    fun `prefers the TV app, else asks`() {
        val phone = app("Кинопоиск", "ru.kinopoisk", tv = false)
        assertEquals(AppMatch.Found(kinopoisk), matcher.match("кинопоиск", apps + phone))
        val twin = app("Кинопоиск", "ru.kinopoisk.beta")
        assertEquals(AppMatch.Ambiguous(listOf(kinopoisk, twin)), matcher.match("кинопоиск", apps + twin))
    }

    @Test
    fun `skeletons make spellings meet`() {
        assertEquals(skeleton("youtube"), skeleton("ютуб"))
        assertEquals(skeleton("VK Video"), skeleton("вк видео"))
    }
}
