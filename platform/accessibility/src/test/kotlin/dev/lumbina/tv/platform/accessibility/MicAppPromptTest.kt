package dev.lumbina.tv.platform.accessibility

import android.view.KeyEvent
import dev.lumbina.tv.core.domain.access.AccessSettings
import dev.lumbina.tv.core.testing.FakeSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MicAppPromptTest {

    private class Card(var fits: Boolean = true) : PromptCard {
        var message: String? = null
        var buttons: List<String> = emptyList()
        override var selected = 0

        override fun show(message: String, buttons: List<String>, fraction: Float?, hint: String?): Boolean {
            if (!fits) return false
            this.message = message
            // As the card on the device: the choice stays while the same buttons are shown again.
            if (buttons != this.buttons) selected = 0
            this.buttons = buttons
            return true
        }

        override fun move(step: Int) {
            selected = (selected + step).coerceIn(0, buttons.lastIndex.coerceAtLeast(0))
        }

        override fun hide() {
            message = null
            buttons = emptyList()
        }
    }

    private val cinema = "tv.cinema"
    private val card = Card()
    private val settings = FakeSettings(AccessSettings())
    private var front: String? = cinema
    private val texts = MicAppPrompt.Texts(
        question = { "$it?" },
        later = "later",
        allow = "allow",
        never = "never",
        allowed = "done",
        label = { "Cinema" },
        failed = "failed",
        neverNote = "won't ask",
    )

    private fun TestScope.prompt() = MicAppPrompt(card, texts, settings, { front }, this) { _, _ -> }

    private fun down(code: Int, repeat: Int = 0) = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, repeat)

    private fun up(code: Int) = KeyEvent(KeyEvent.ACTION_UP, code)

    private fun MicAppPrompt.press(code: Int): Boolean = onKey(down(code)) and onKey(up(code))

    private val ok = KeyEvent.KEYCODE_DPAD_CENTER
    private val right = KeyEvent.KEYCODE_DPAD_RIGHT

    @Test
    fun `the app on screen is asked about, by its name`() = runTest {
        val prompt = prompt()
        prompt.ask(cinema)
        runCurrent()
        assertEquals("Cinema?", card.message)
        assertEquals(listOf("later", "allow", "never"), card.buttons)
        prompt.hide()
    }

    @Test
    fun `an app behind another one, or answered about, is not asked about`() = runTest {
        val prompt = prompt()
        front = "tv.other"
        prompt.ask(cinema)
        runCurrent()
        assertNull(card.message)
        front = cinema
        settings.current.value = AccessSettings(micApps = mapOf(cinema to false))
        prompt.ask(cinema)
        runCurrent()
        assertNull(card.message)
    }

    @Test
    fun `OK on the first button, Back and time passing decide nothing`() = runTest {
        val prompt = prompt()
        prompt.ask(cinema)
        runCurrent()
        assertTrue(prompt.press(ok))
        assertNull(card.message)
        prompt.ask(cinema)
        runCurrent()
        assertTrue(prompt.press(KeyEvent.KEYCODE_BACK))
        assertNull(card.message)
        prompt.ask(cinema)
        runCurrent()
        advanceTimeBy(MicAppPrompt.ASK_MILLIS + 1)
        assertNull(card.message)
        assertTrue(settings.current.value.micApps.isEmpty())
    }

    @Test
    fun `allow and never are kept, and allow says to record again`() = runTest {
        val prompt = prompt()
        prompt.ask(cinema)
        runCurrent()
        prompt.press(right)
        prompt.press(ok)
        runCurrent()
        assertEquals(mapOf(cinema to true), settings.current.value.micApps)
        assertEquals("done", card.message)
        advanceTimeBy(MicAppPrompt.DONE_MILLIS + 1)
        assertNull(card.message)

        settings.current.value = AccessSettings()
        prompt.ask(cinema)
        runCurrent()
        prompt.press(right)
        prompt.press(right)
        prompt.press(ok)
        runCurrent()
        assertEquals(mapOf(cinema to false), settings.current.value.micApps)
        assertEquals("won't ask", card.message)
        advanceTimeBy(MicAppPrompt.NEVER_MILLIS + 1)
        assertNull(card.message)
    }

    @Test
    fun `allowed, the card waits for the sound and says when it could not be turned on`() = runTest {
        val prompt = prompt()
        prompt.started = { false }
        var opened = false
        prompt.openAccess = { opened = true }
        prompt.ask(cinema)
        runCurrent()
        prompt.press(right)
        prompt.press(ok)
        runCurrent()
        assertEquals("failed", card.message)
        assertTrue(prompt.press(ok))
        assertTrue(opened)
        assertNull(card.message)
    }

    @Test
    fun `the answer's card goes with the app, and the question waits for another card`() = runTest {
        val prompt = prompt()
        prompt.ask(cinema)
        runCurrent()
        prompt.press(right)
        prompt.press(ok)
        runCurrent()
        front = "tv.other"
        prompt.onWindowChanged()
        assertNull(card.message)

        front = cinema
        settings.current.value = AccessSettings()
        var busy = true
        prompt.paused = { busy }
        prompt.ask(cinema)
        runCurrent()
        advanceTimeBy(MicAppPrompt.ASK_MILLIS * 2)
        assertEquals("Cinema?", card.message)
        busy = false
        advanceTimeBy(MicAppPrompt.ASK_MILLIS + 1_000)
        assertNull(card.message)
    }

    @Test
    fun `a key held since before the card showed stays the app's and answers nothing`() = runTest {
        val prompt = prompt()
        assertFalse(prompt.onKey(down(ok)))
        prompt.ask(cinema)
        runCurrent()
        assertFalse(prompt.onKey(down(ok, repeat = 1)))
        assertFalse(prompt.onKey(up(ok)))
        assertEquals("Cinema?", card.message)
        prompt.hide()
    }

    @Test
    fun `the key that answered is the card's until it is let go`() = runTest {
        val prompt = prompt()
        prompt.ask(cinema)
        runCurrent()
        assertTrue(prompt.onKey(down(ok)))
        assertNull(card.message)
        assertTrue(prompt.onKey(down(ok, repeat = 1)))
        assertTrue(prompt.onKey(up(ok)))
        assertFalse(prompt.onKey(down(ok)))
    }

    @Test
    fun `the arrows are the card's while it shows, other keys the app's`() = runTest {
        val prompt = prompt()
        prompt.ask(cinema)
        runCurrent()
        assertTrue(prompt.press(KeyEvent.KEYCODE_DPAD_DOWN))
        assertFalse(prompt.onKey(down(KeyEvent.KEYCODE_VOLUME_UP)))
        assertEquals("Cinema?", card.message)
        prompt.hide()
    }

    @Test
    fun `the question goes when its app leaves the screen, and the key goes to what is there`() = runTest {
        val prompt = prompt()
        prompt.ask(cinema)
        runCurrent()
        front = null
        prompt.onWindowChanged()
        assertNull(card.message)

        front = cinema
        prompt.ask(cinema)
        runCurrent()
        front = "tv.other"
        assertFalse(prompt.onKey(down(ok)))
        assertNull(card.message)
        assertTrue(settings.current.value.micApps.isEmpty())
    }

    @Test
    fun `a card that can't be shown takes no keys`() = runTest {
        card.fits = false
        val prompt = prompt()
        prompt.ask(cinema)
        runCurrent()
        assertFalse(prompt.onKey(down(ok)))
    }

    @Test
    fun `a second question waits for the first to be answered`() = runTest {
        val prompt = prompt()
        prompt.ask(cinema)
        prompt.ask("tv.radio")
        runCurrent()
        assertEquals("Cinema?", card.message)
        prompt.hide()
    }
}
