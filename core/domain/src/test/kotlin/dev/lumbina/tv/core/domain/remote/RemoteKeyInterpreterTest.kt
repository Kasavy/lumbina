package dev.lumbina.tv.core.domain.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteKeyInterpreterTest {

    private val homeScan = 102
    private val otherScan = 105
    private val micScan = 166
    private val keyMap = KeyMap { scan, _ ->
        when (scan) {
            homeScan -> RemoteKey.Home
            micScan -> RemoteKey.Mic
            else -> null
        }
    }
    private val actions = mutableListOf<RemoteAction>()

    private fun TestScope.interpreter(
        voice: Boolean = false,
        policy: KeyBindingPolicy = KeyBindingPolicy.defaults(voiceAvailable = voice),
    ) = RemoteKeyInterpreter(
        keyMap = keyMap,
        policy = policy,
        scope = backgroundScope,
        onAction = { actions += it },
        now = { testScheduler.currentTime },
        longPressMillis = 600,
    )

    private fun down(scan: Int = homeScan) = RawKey(scan, 0, KeyAction.Down)
    private fun up(scan: Int = homeScan) = RawKey(scan, 0, KeyAction.Up)

    @Test
    fun `short press opens Lumbina`() = runTest {
        val interpreter = interpreter()
        assertTrue(interpreter.onKey(down()))
        advanceTimeBy(200)
        assertTrue(interpreter.onKey(up()))
        runCurrent()
        assertTrue(actions.isEmpty()) // waiting for a possible second press
        advanceTimeBy(301)
        assertEquals(listOf(RemoteAction.OpenLumbina), actions)
    }

    @Test
    fun `a double press on Home shows recent apps and does not open Lumbina`() = runTest {
        val interpreter = interpreter()
        interpreter.onKey(down())
        advanceTimeBy(100)
        interpreter.onKey(up())
        advanceTimeBy(150)
        interpreter.onKey(down())
        advanceTimeBy(100)
        interpreter.onKey(up())
        advanceTimeBy(1_000)
        assertEquals(listOf(RemoteAction.ShowAppSwitcher), actions)
    }

    @Test
    fun `with the double press turned off Home answers at once, and twice for two presses`() = runTest {
        val asDevice = KeyBinding(RemoteKey.Home, KeyGesture.DoubleTap, action = null)
        val interpreter = interpreter(policy = KeyBindingPolicy.defaults(own = listOf(asDevice)))
        interpreter.onKey(down())
        advanceTimeBy(100)
        interpreter.onKey(up())
        assertEquals(listOf(RemoteAction.OpenLumbina), actions)
        advanceTimeBy(100)
        interpreter.onKey(down())
        advanceTimeBy(100)
        interpreter.onKey(up())
        assertEquals(listOf(RemoteAction.OpenLumbina, RemoteAction.OpenLumbina), actions)
    }

    @Test
    fun `presses further apart are two short presses`() = runTest {
        val interpreter = interpreter()
        repeat(2) {
            interpreter.onKey(down())
            advanceTimeBy(100)
            interpreter.onKey(up())
            advanceTimeBy(500)
        }
        assertEquals(listOf(RemoteAction.OpenLumbina, RemoteAction.OpenLumbina), actions)
    }

    @Test
    fun `long press opens the quick menu once and not Lumbina`() = runTest {
        val interpreter = interpreter()
        interpreter.onKey(down())
        advanceTimeBy(601)
        interpreter.onKey(up())
        runCurrent()
        assertEquals(listOf(RemoteAction.ShowQuickMenu), actions)
    }

    @Test
    fun `auto-repeat downs are ignored`() = runTest {
        val interpreter = interpreter()
        interpreter.onKey(down())
        advanceTimeBy(100)
        assertTrue(interpreter.onKey(down().copy(repeat = true)))
        advanceTimeBy(100)
        interpreter.onKey(down().copy(repeat = true))
        interpreter.onKey(up())
        advanceTimeBy(301)
        assertEquals(listOf(RemoteAction.OpenLumbina), actions)
    }

    @Test
    fun `without a long-press binding a long hold still opens Lumbina on release`() = runTest {
        val shortOnly = KeyBindingPolicy(mapOf((RemoteKey.Home to KeyGesture.ShortPress) to RemoteAction.OpenLumbina))
        val interpreter = interpreter(policy = shortOnly)
        interpreter.onKey(down())
        advanceTimeBy(2_000)
        interpreter.onKey(up())
        advanceTimeBy(301)
        assertEquals(listOf(RemoteAction.OpenLumbina), actions)
    }

    @Test
    fun `unmapped keys pass through`() = runTest {
        val interpreter = interpreter()
        assertFalse(interpreter.onKey(down(otherScan)))
        assertFalse(interpreter.onKey(up(otherScan)))
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `mic is push-to-talk when voice is available, independent of Home`() = runTest {
        val interpreter = interpreter(voice = true)
        interpreter.onKey(down(micScan))
        advanceTimeBy(2_000)
        interpreter.onKey(down(micScan).copy(repeat = true))
        interpreter.onKey(down(homeScan)) // Home pressed while talking
        interpreter.onKey(up(homeScan))
        interpreter.onKey(up(micScan))
        advanceTimeBy(301)
        assertEquals(
            listOf(RemoteAction.StartVoice, RemoteAction.StopVoice, RemoteAction.OpenLumbina),
            actions,
        )
    }

    @Test
    fun `a quick mic tap cancels instead of stopping, a double tap cycles the language`() = runTest {
        val interpreter = interpreter(voice = true)
        interpreter.onKey(down(micScan))
        advanceTimeBy(100)
        interpreter.onKey(up(micScan)) // tap 1
        advanceTimeBy(200)
        interpreter.onKey(down(micScan))
        advanceTimeBy(100)
        interpreter.onKey(up(micScan)) // tap 2, 300 ms after tap 1
        runCurrent()
        assertEquals(
            listOf(
                RemoteAction.StartVoice,
                RemoteAction.CancelVoice,
                RemoteAction.StartVoice,
                RemoteAction.CancelVoice,
                RemoteAction.CycleVoiceLanguage,
            ),
            actions,
        )
    }

    @Test
    fun `taps far apart are not a double tap, and a hold is speech`() = runTest {
        val interpreter = interpreter(voice = true)
        interpreter.onKey(down(micScan))
        advanceTimeBy(100)
        interpreter.onKey(up(micScan))
        advanceTimeBy(2_000)
        interpreter.onKey(down(micScan))
        advanceTimeBy(100)
        interpreter.onKey(up(micScan))
        interpreter.onKey(down(micScan))
        advanceTimeBy(1_000)
        interpreter.onKey(up(micScan))
        runCurrent()
        assertEquals(RemoteAction.StopVoice, actions.last())
        assertTrue(RemoteAction.CycleVoiceLanguage !in actions)
    }

    @Test
    fun `mic passes through to the firmware without voice`() = runTest {
        val interpreter = interpreter(voice = false)
        assertFalse(interpreter.onKey(down(micScan)))
        assertFalse(interpreter.onKey(up(micScan)))
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `up without a preceding down goes on to the firmware, which saw the down`() = runTest {
        val interpreter = interpreter()
        assertFalse(interpreter.onKey(up()))
        runCurrent()
        assertTrue(actions.isEmpty())
    }
}
