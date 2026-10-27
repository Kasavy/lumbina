package dev.lumbina.tv.feature.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveKeysTest {

    private val acted = mutableListOf<Key>()
    private val keys = MoveKeys()

    private fun down(key: Key, repeat: Int = 0) = keys.handle(KeyEventType.KeyDown, key, repeat, acted::add)

    private fun up(key: Key) = keys.handle(KeyEventType.KeyUp, key, 0, acted::add)

    @Test
    fun `the release of the OK held to pick the row up does nothing, and the row gets it`() {
        assertTrue(down(Key.DirectionCenter, repeat = 3))
        assertFalse(up(Key.DirectionCenter))

        assertEquals(emptyList<Key>(), acted)
    }

    @Test
    fun `arrows act when pressed, repeats too are ignored`() {
        down(Key.DirectionDown)
        down(Key.DirectionDown, repeat = 1)
        up(Key.DirectionDown)
        down(Key.DirectionUp)

        assertEquals(listOf(Key.DirectionDown, Key.DirectionUp), acted)
    }

    @Test
    fun `OK puts the row down on release, so the release is not a click`() {
        down(Key.DirectionCenter)
        assertEquals(emptyList<Key>(), acted)

        up(Key.DirectionCenter)
        assertEquals(listOf(Key.DirectionCenter), acted)
    }

    @Test
    fun `Back cancels on release`() {
        down(Key.Back)
        up(Key.Back)

        assertEquals(listOf(Key.Back), acted)
    }

    @Test
    fun `the release of an arrow or Back pressed before the move goes on and does nothing`() {
        assertFalse(up(Key.DirectionDown))
        assertFalse(up(Key.Back))

        assertEquals(emptyList<Key>(), acted)
    }

    @Test
    fun `every press is taken while moving, with its release`() {
        assertTrue(down(Key.DirectionLeft))
        assertTrue(up(Key.DirectionLeft))
        assertTrue(down(Key.Back))
        assertTrue(up(Key.Back))
    }
}
