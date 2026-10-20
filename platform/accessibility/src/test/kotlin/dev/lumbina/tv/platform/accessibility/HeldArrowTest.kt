package dev.lumbina.tv.platform.accessibility

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class HeldArrowTest {

    private val steps = mutableListOf<Pair<Int, Boolean>>()
    private var showing = true
    private var onStep: () -> Unit = {}
    private val arrow = HeldArrow(
        Handler(Looper.getMainLooper()),
        showing = { showing },
        timing = HeldArrow.Timing(firstMillis = 500, everyMillis = 50),
    ) { key, repeated ->
        steps += key to repeated
        onStep()
    }

    private fun idle(millis: Long) = ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)

    private fun event(action: Int, key: Int = DOWN, repeat: Int = 0) = KeyEvent(0, 0, action, key, repeat)

    @Test
    fun `one step on the press, then a repeat after the wait and one every step's time until released`() {
        arrow.onKey(event(KeyEvent.ACTION_DOWN))
        assertEquals(listOf(DOWN to false), steps)
        idle(499)
        assertEquals(1, steps.size)
        idle(1 + 100)
        assertEquals(listOf(DOWN to false, DOWN to true, DOWN to true, DOWN to true), steps)
        arrow.onKey(event(KeyEvent.ACTION_UP))
        idle(1_000)
        assertEquals(4, steps.size)
    }

    @Test
    fun `the device's own repeats do nothing, another key's release leaves the held one going`() {
        arrow.onKey(event(KeyEvent.ACTION_DOWN))
        arrow.onKey(event(KeyEvent.ACTION_DOWN, repeat = 1))
        arrow.onKey(event(KeyEvent.ACTION_UP, key = KeyEvent.KEYCODE_DPAD_UP))
        idle(500)
        assertEquals(listOf(DOWN to false, DOWN to true), steps)
    }

    @Test
    fun `once the overlay is gone, the repeat stops though the release never came`() {
        arrow.onKey(event(KeyEvent.ACTION_DOWN))
        showing = false
        idle(1_000)
        assertEquals(1, steps.size)
        assertEquals(false, arrow.isHeld)
    }

    @Test
    fun `a step that lets go is not undone`() {
        onStep = { arrow.letGo() }
        arrow.press(DOWN)
        idle(1_000)
        assertEquals(1, steps.size)
    }

    @Test
    fun `a new press goes on with the new key`() {
        arrow.press(DOWN)
        arrow.press(KeyEvent.KEYCODE_DPAD_UP)
        idle(500)
        assertEquals(KeyEvent.KEYCODE_DPAD_UP to true, steps.last())
        assertEquals(listOf(DOWN, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP), steps.map { it.first })
    }

    private companion object {
        const val DOWN = KeyEvent.KEYCODE_DPAD_DOWN
    }
}
