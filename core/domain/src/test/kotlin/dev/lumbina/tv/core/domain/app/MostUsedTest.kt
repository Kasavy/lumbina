package dev.lumbina.tv.core.domain.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MostUsedTest {

    private fun app(pkg: String) = App(AppId(pkg, "$pkg.Main"), pkg, isTvApp = true)

    private val apps = listOf(app("a"), app("b"), app("c"), app("d"))

    @Test
    fun `most opened first, never opened left out`() {
        val usage = mapOf("a" to 1L, "c" to 5L, "d" to 0L)
        assertEquals(listOf("c", "a"), mostUsed(apps, usage, max = 6).map { it.id.packageName })
    }

    @Test
    fun `leaves out the excepted apps and keeps the given order on ties`() {
        val usage = mapOf("a" to 2L, "b" to 2L, "c" to 2L)
        val result = mostUsed(apps, usage, max = 2, except = setOf(apps[0].id))
        assertEquals(listOf("b", "c"), result.map { it.id.packageName })
    }
}
