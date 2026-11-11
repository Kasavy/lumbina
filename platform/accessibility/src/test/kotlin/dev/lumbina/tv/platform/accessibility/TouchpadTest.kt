package dev.lumbina.tv.platform.accessibility

import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a step of the touchpad's two-finger swipe goes. */
class TouchpadTest {

    private val overlaySteps = mutableListOf<String>()
    private val arrows = mutableListOf<String>()
    private val pointer = object : Pointer {
        val scrolled = mutableListOf<String>()

        override fun move(dx: Float, dy: Float) = Unit

        override fun click(long: Boolean) = false

        override fun scroll(dir: String): Boolean {
            scrolled += dir
            return true
        }

        override fun hide() = Unit
    }

    private fun touchpad(hasArrows: Boolean, overlayShows: Boolean = false) = Touchpad(
        pointer,
        hasArrows,
        overlay = { dir -> overlayShows.also { if (it) overlaySteps += dir } },
        arrow = { dir -> arrows.add(dir) },
        main = EmptyCoroutineContext,
    )

    @Test
    fun `with arrows a step is an arrow`() = runBlocking {
        assertTrue(touchpad(hasArrows = true).scroll("down"))
        assertEquals(listOf("down"), arrows)
        assertTrue(pointer.scrolled.isEmpty())
    }

    @Test
    fun `without arrows the list under the pointer is scrolled`() = runBlocking {
        assertTrue(touchpad(hasArrows = false).scroll("up"))
        assertEquals(listOf("up"), pointer.scrolled)
        assertTrue(arrows.isEmpty())
    }

    @Test
    fun `Lumbina's overlay takes a step first, with arrows or without`() = runBlocking {
        listOf(true, false).forEach { hasArrows ->
            assertTrue(touchpad(hasArrows, overlayShows = true).scroll("right"))
        }
        assertEquals(listOf("right", "right"), overlaySteps)
        assertTrue(arrows.isEmpty() && pointer.scrolled.isEmpty())
    }

    @Test
    fun `any other word is no step`() = runBlocking {
        listOf(null, "", "ok", "back").forEach { assertFalse(touchpad(hasArrows = true).scroll(it)) }
        assertTrue(arrows.isEmpty())
    }
}
