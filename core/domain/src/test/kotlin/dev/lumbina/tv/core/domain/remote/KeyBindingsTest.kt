package dev.lumbina.tv.core.domain.remote

import dev.lumbina.tv.core.domain.app.AppId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyBindingsTest {
    private val red = RemoteKey.ofCode(183)
    private val cinema = RemoteAction.OpenApp(AppId("tv.cinema", "tv.cinema.Main"))

    @Test
    fun `a button known by its code can be bound, the ones a remote can't do without can not`() {
        assertEquals(183, red.keyCode)
        assertNull(RemoteKey.Home.keyCode)
        assertTrue(red.canBeBound)
        assertTrue(RemoteKey.Home.canBeBound)
        // OK, Back, an arrow, power, volume; a gamepad's A and B, the TV's power, sleep, a diagonal.
        listOf(23, 4, 19, 26, 24, 96, 97, 177, 223, 268).forEach {
            assertFalse(RemoteKey.ofCode(it).canBeBound, "key $it")
        }
    }

    @Test
    fun `the user's bindings lie over the defaults, and one with no action takes a default off`() {
        val own = listOf(
            KeyBinding(red, KeyGesture.ShortPress, cinema),
            KeyBinding(RemoteKey.Home, KeyGesture.DoubleTap, action = null),
            KeyBinding(RemoteKey.Home, KeyGesture.LongPress, RemoteAction.OpenAllApps),
        )
        val policy = KeyBindingPolicy.defaults(own = own)
        assertEquals(cinema, policy.actionFor(red, KeyGesture.ShortPress))
        assertEquals(RemoteAction.OpenLumbina, policy.actionFor(RemoteKey.Home, KeyGesture.ShortPress))
        assertNull(policy.actionFor(RemoteKey.Home, KeyGesture.DoubleTap))
        assertEquals(RemoteAction.OpenAllApps, policy.actionFor(RemoteKey.Home, KeyGesture.LongPress))
    }

    @Test
    fun `a stored binding can't take the arrows, nor a gesture that is not the user's to set`() {
        val own = listOf(
            KeyBinding(RemoteKey.ofCode(20), KeyGesture.ShortPress, cinema),
            KeyBinding(RemoteKey.Mic, KeyGesture.Down, RemoteAction.Nothing),
        )
        val policy = KeyBindingPolicy.defaults(voiceAvailable = true, own = own)
        assertFalse(policy.handles(RemoteKey.ofCode(20)))
        assertEquals(RemoteAction.StartVoice, policy.actionFor(RemoteKey.Mic, KeyGesture.Down))
    }

    @Test
    fun `a key left with no gesture is the firmware's again`() {
        val own = BINDABLE_GESTURES.map { KeyBinding(RemoteKey.Home, it, action = null) }
        assertFalse(KeyBindingPolicy.defaults(own = own).handles(RemoteKey.Home))
    }

    @Test
    fun `settings keep only what differs from the defaults, and give a key back whole`() {
        val set = RemoteSettings()
            .withBinding(red, KeyGesture.ShortPress, cinema)
            .withBinding(RemoteKey.Home, KeyGesture.DoubleTap, null)
        assertFalse(set.doubleHomeRecents)
        assertEquals(2, set.bindings.size)

        val back = set.withBinding(RemoteKey.Home, KeyGesture.DoubleTap, RemoteAction.ShowAppSwitcher)
        assertTrue(back.doubleHomeRecents)
        assertEquals(listOf(KeyBinding(red, KeyGesture.ShortPress, cinema)), back.bindings)
        assertTrue(back.withoutBindings(red).bindings.isEmpty())
    }

    @Test
    fun `a button takes a name of the user's, a blank one gives its own back, and freeing it forgets the name`() {
        val named = RemoteSettings().withKeyName(red, " Films ")
        assertEquals(mapOf(red to "Films"), named.keyNames)
        assertTrue(named.withKeyName(red, " ").keyNames.isEmpty())
        assertTrue(named.withoutBindings(red).keyNames.isEmpty())
    }

    @Test
    fun `a bound button is taken from the firmware and does what it was given`() = runTest {
        val actions = mutableListOf<RemoteAction>()
        val interpreter = RemoteKeyInterpreter(
            keyMap = { _, _ -> null },
            policy = KeyBindingPolicy.defaults(own = listOf(KeyBinding(red, KeyGesture.ShortPress, cinema))),
            scope = backgroundScope,
            onAction = { actions += it },
            now = { testScheduler.currentTime },
        )
        assertTrue(interpreter.onKey(RawKey(0, 183, KeyAction.Down)))
        advanceTimeBy(100)
        assertTrue(interpreter.onKey(RawKey(0, 183, KeyAction.Up)))
        assertEquals(listOf<RemoteAction>(cinema), actions)
        // A button nobody bound stays the firmware's.
        assertFalse(interpreter.onKey(RawKey(0, 184, KeyAction.Down)))
    }

    @Test
    fun `a button limited to Lumbina is the firmware's in another app, press and release alike`() = runTest {
        val actions = mutableListOf<RemoteAction>()
        var inLumbina = false
        val interpreter = RemoteKeyInterpreter(
            keyMap = { _, _ -> null },
            policy = KeyBindingPolicy.defaults(
                own = listOf(KeyBinding(red, KeyGesture.ShortPress, RemoteAction.ShowQuickMenu)),
                lumbinaOnly = setOf(red, RemoteKey.Home),
            ),
            scope = backgroundScope,
            onAction = { actions += it },
            now = { testScheduler.currentTime },
            inLumbina = { inLumbina },
        )
        assertFalse(interpreter.onKey(RawKey(0, 183, KeyAction.Down)))
        assertFalse(interpreter.onKey(RawKey(0, 183, KeyAction.Up)))
        assertTrue(actions.isEmpty())

        inLumbina = true
        assertTrue(interpreter.onKey(RawKey(0, 183, KeyAction.Down)))
        // The screen changes while the key is down: its release is still Lumbina's.
        inLumbina = false
        assertTrue(interpreter.onKey(RawKey(0, 183, KeyAction.Up)))
        assertEquals(listOf<RemoteAction>(RemoteAction.ShowQuickMenu), actions)
        // Home is never limited: it is how Lumbina is come back to.
        assertTrue(KeyBindingPolicy.defaults(lumbinaOnly = setOf(RemoteKey.Home)).handles(RemoteKey.Home, false))
    }

    @Test
    fun `Menu shows the quick menu on Lumbina's screens by itself, and the user may change either`() {
        val usual = KeyBindingPolicy.defaults(lumbinaOnly = RemoteSettings().limited)
        assertEquals(RemoteAction.ShowQuickMenu, usual.actionFor(RemoteKey.Menu, KeyGesture.ShortPress))
        assertTrue(usual.handles(RemoteKey.Menu, inLumbina = true))
        assertFalse(usual.handles(RemoteKey.Menu, inLumbina = false))

        val everywhere = RemoteSettings().withLumbinaOnly(RemoteKey.Menu, only = false)
        assertFalse(RemoteKey.Menu in everywhere.limited)
        assertTrue(RemoteKey.Menu in everywhere.withLumbinaOnly(RemoteKey.Menu, only = true).limited)
        assertTrue(RemoteKey.Menu in everywhere.withoutBindings().limited)

        val left = RemoteSettings().withBinding(RemoteKey.Menu, KeyGesture.ShortPress, null)
        assertFalse(KeyBindingPolicy.defaults(own = left.bindings).handles(RemoteKey.Menu))
    }

    @Test
    fun `a button being tried tells its gestures and does nothing`() = runTest {
        val actions = mutableListOf<RemoteAction>()
        val seen = mutableListOf<KeyGesture>()
        val interpreter = RemoteKeyInterpreter(
            keyMap = { _, _ -> null },
            policy = KeyBindingPolicy.defaults(
                own = listOf(
                    KeyBinding(red, KeyGesture.ShortPress, cinema),
                    KeyBinding(red, KeyGesture.LongPress, RemoteAction.ShowQuickMenu),
                ),
            ),
            scope = backgroundScope,
            onAction = { actions += it },
            now = { testScheduler.currentTime },
            probe = { key, gesture -> (key == red).also { if (it) seen += gesture } },
        )
        interpreter.onKey(RawKey(0, 183, KeyAction.Down))
        advanceTimeBy(100)
        interpreter.onKey(RawKey(0, 183, KeyAction.Up))
        interpreter.onKey(RawKey(0, 183, KeyAction.Down))
        advanceTimeBy(700)
        interpreter.onKey(RawKey(0, 183, KeyAction.Up))
        assertEquals(listOf(KeyGesture.ShortPress, KeyGesture.LongPress), seen)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `of the mic button only the double press is the user's, and only where Lumbina takes the button`() {
        val own = listOf(
            KeyBinding(RemoteKey.Mic, KeyGesture.DoubleTap, cinema),
            KeyBinding(RemoteKey.Mic, KeyGesture.LongPress, cinema),
        )
        val heard = KeyBindingPolicy.defaults(voiceAvailable = true, own = own)
        assertEquals(cinema, heard.actionFor(RemoteKey.Mic, KeyGesture.DoubleTap))
        assertNull(heard.actionFor(RemoteKey.Mic, KeyGesture.LongPress))
        assertEquals(RemoteAction.StartVoice, heard.actionFor(RemoteKey.Mic, KeyGesture.Down))
        assertFalse(KeyBindingPolicy.defaults(voiceAvailable = false, own = own).handles(RemoteKey.Mic))
    }

    @Test
    fun `a key passed on at its press is passed on while held, whatever comes in front`() = runTest {
        var inLumbina = false
        val actions = mutableListOf<RemoteAction>()
        val interpreter = RemoteKeyInterpreter(
            keyMap = { _, _ -> null },
            policy = KeyBindingPolicy.defaults(),
            scope = backgroundScope,
            onAction = { actions += it },
            now = { testScheduler.currentTime },
            inLumbina = { inLumbina },
        )
        assertFalse(interpreter.onKey(RawKey(0, 82, KeyAction.Down)))
        inLumbina = true
        assertFalse(interpreter.onKey(RawKey(0, 82, KeyAction.Down, repeat = true)))
        assertFalse(interpreter.onKey(RawKey(0, 82, KeyAction.Up)))
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `a press after a release that never came is a press of its own`() = runTest {
        val actions = mutableListOf<RemoteAction>()
        val interpreter = RemoteKeyInterpreter(
            keyMap = { _, _ -> null },
            policy = KeyBindingPolicy.defaults(own = listOf(KeyBinding(red, KeyGesture.LongPress, cinema))),
            scope = backgroundScope,
            onAction = { actions += it },
            now = { testScheduler.currentTime },
        )
        interpreter.onKey(RawKey(0, 183, KeyAction.Down))
        advanceTimeBy(700)
        interpreter.onKey(RawKey(0, 183, KeyAction.Down))
        advanceTimeBy(700)
        interpreter.onKey(RawKey(0, 183, KeyAction.Up))
        assertEquals(listOf<RemoteAction>(cinema, cinema), actions)
    }

    @Test
    fun `as on the device where Lumbina does nothing by itself leaves nothing of the user's`() {
        val bound = RemoteSettings().withBinding(red, KeyGesture.ShortPress, cinema)
        assertTrue(bound.withBinding(red, KeyGesture.ShortPress, null).bindings.isEmpty())
        assertFalse(RemoteKey.Home in RemoteSettings(lumbinaOnly = setOf(RemoteKey.Home)).limited)
    }
}
