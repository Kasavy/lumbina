package dev.lumbina.tv.core.domain.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecentAppsTest {

    private val recent = RecentApps(tracked = { it != "launcher" && it != "keyboard" }, capacity = 3)

    @Test
    fun `most recent first, without duplicates, capped`() {
        listOf("a", "b", "a", "c", "d").forEach(recent::onForeground)
        assertEquals(listOf("d", "c", "a"), recent.recent)
    }

    @Test
    fun `untracked packages are not listed but become current`() {
        recent.onForeground("a")
        recent.onForeground("keyboard")
        assertEquals(listOf("a"), recent.recent)
        assertEquals("keyboard", recent.current)
    }

    @Test
    fun `the switcher starts on the previous app while one is in front`() {
        recent.onForeground("a")
        recent.onForeground("b")
        assertEquals(1, recent.switchTarget())
        recent.onForeground("launcher")
        assertEquals(0, recent.switchTarget())
        assertEquals(1, recent.switchTarget(inFront = "b")) // what the screen says wins
    }

    @Test
    fun `forgotten apps leave the list`() {
        recent.onForeground("a")
        recent.onForeground("b")
        recent.forget("a")
        assertEquals(listOf("b"), recent.recent)
    }

    @Test
    fun `a saved list goes after what was seen since, untracked dropped, capped`() {
        recent.onForeground("x")
        recent.restore(listOf("a", "x", "keyboard", "b", "c"))
        assertEquals(listOf("x", "a", "b"), recent.recent)
    }
}
