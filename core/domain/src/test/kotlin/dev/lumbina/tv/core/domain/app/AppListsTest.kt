package dev.lumbina.tv.core.domain.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppListsTest {

    private val day = 24 * 60 * 60_000L
    private val now = 100 * day

    private fun app(name: String, daysAgo: Int?, activity: String = "Main") =
        App(AppId(name, "$name.$activity"), name, isTvApp = true, installedAt = daysAgo?.let { now - it * day } ?: 0)

    @Test
    fun `recently installed are newest first, only the last two weeks, apps without a date left out`() {
        val apps = listOf(app("old", 30), app("week", 7), app("today", 0), app("unknown", null), app("edge", 14))
        assertEquals(listOf("today", "week"), recentlyInstalled(apps, now).map { it.label })
    }

    @Test
    fun `at most ten recently installed`() {
        val apps = (1..12).map { app("a$it", 1) }
        assertEquals(10, recentlyInstalled(apps, now).size)
    }

    @Test
    fun `recently opened keep the switcher's order, one entry per package, unknown ones left out`() {
        val apps = listOf(app("a", 1), app("a", 1, "Other"), app("b", 1))
        val opened = recentlyOpened(apps, listOf("b", "gone", "a"))
        assertEquals(listOf("b.Main", "a.Main"), opened.map { it.id.activityName })
    }

    @Test
    fun `labels by package take the first entry`() {
        val apps = listOf(App(AppId("a", "a.Main"), "First", true), App(AppId("a", "a.Other"), "Second", true))
        assertEquals(mapOf("a" to "First"), labelsByPackage(apps))
    }
}
