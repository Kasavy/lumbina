package dev.lumbina.tv.platform.accessibility

import android.view.KeyEvent
import dev.lumbina.tv.core.domain.access.AccessSettings
import dev.lumbina.tv.core.testing.FakeSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DebuggingWarningTest {

    private class Card : PromptCard {
        var message: String? = null
        var hint: String? = null
        var buttons: List<String> = emptyList()
        override var selected = 0

        override fun show(message: String, buttons: List<String>, fraction: Float?, hint: String?): Boolean {
            this.message = message
            this.hint = hint
            this.buttons = buttons
            selected = 0
            return true
        }

        override fun move(step: Int) {
            selected = (selected + step).coerceIn(0, buttons.lastIndex.coerceAtLeast(0))
        }

        override fun hide() {
            message = null
        }
    }

    private val card = Card()
    private val settings = FakeSettings(AccessSettings())
    private var on = false
    private var opened = 0
    private var others = false
    private var keeps = true

    private fun TestScope.warning(voiceOverIt: Boolean = true): DebuggingWarning {
        val device = DebuggingWarning.Device(
            keepsService = { keeps },
            debuggingOn = { on },
            openAccess = { opened++ },
            othersShowing = { others },
            voiceOverIt = voiceOverIt,
        )
        val io = StandardTestDispatcher(testScheduler)
        return DebuggingWarning(RuntimeEnvironment.getApplication(), device, settings, card, { _, _ -> }, io)
            .also { it.start(this) }
    }

    private fun down(code: Int, repeat: Int = 0) = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, repeat)

    private fun up(code: Int) = KeyEvent(KeyEvent.ACTION_UP, code)

    private val ok = KeyEvent.KEYCODE_DPAD_CENTER
    private val right = KeyEvent.KEYCODE_DPAD_RIGHT

    @Test
    fun `shown once debugging is off, and connect opens Access`() = runTest {
        val warning = warning()
        advanceUntilIdle()
        assertTrue(card.message != null)
        assertTrue(warning.onKey(down(ok)))
        assertTrue(warning.onKey(up(ok)))
        assertNull(card.message)
        assertEquals(1, opened)
        warning.stop()
    }

    @Test
    fun `a key held from before goes on to the app, release and all`() = runTest {
        val warning = warning()
        advanceUntilIdle()
        // OK went down in the app before the card came: its repeats and release are the app's.
        assertFalse(warning.onKey(down(ok, repeat = 3)))
        assertFalse(warning.onKey(up(ok)))
        assertTrue(card.message != null)
        assertEquals(0, opened)
        warning.stop()
    }

    @Test
    fun `not now is remembered until debugging is seen on again`() = runTest {
        val warning = warning()
        advanceUntilIdle()
        warning.onKey(down(right))
        warning.onKey(up(right))
        warning.onKey(down(ok))
        warning.onKey(up(ok))
        advanceUntilIdle()
        assertEquals(0, opened)
        assertTrue(settings.settings.first().debuggingOffDismissed)
        warning.lookSoon()
        advanceUntilIdle()
        assertNull(card.message)
        on = true
        warning.lookSoon()
        advanceUntilIdle()
        assertFalse(settings.settings.first().debuggingOffDismissed)
        on = false
        warning.lookSoon()
        advanceUntilIdle()
        assertTrue(card.message != null)
        warning.stop()
    }

    @Test
    fun `another key puts it away for now and goes on`() = runTest {
        val warning = warning()
        advanceUntilIdle()
        assertFalse(warning.onKey(down(KeyEvent.KEYCODE_VOLUME_UP)))
        assertFalse(warning.onKey(up(KeyEvent.KEYCODE_VOLUME_UP)))
        assertNull(card.message)
        assertFalse(settings.settings.first().debuggingOffDismissed)
        warning.stop()
    }

    @Test
    fun `it waits while another overlay has the keys`() = runTest {
        others = true
        val warning = warning()
        advanceTimeBy(30_000L)
        assertNull(card.message)
        others = false
        advanceUntilIdle()
        assertTrue(card.message != null)
        warning.stop()
    }

    @Test
    fun `on a TV it names closing apps only`() = runTest {
        val warning = warning(voiceOverIt = false)
        advanceUntilIdle()
        assertEquals("Without it closing apps doesn’t work", card.hint)
        warning.stop()
    }

    @Test
    fun `one who never connected is not told`() = runTest {
        keeps = false
        val warning = warning(voiceOverIt = false)
        advanceUntilIdle()
        assertNull(card.message)
        warning.stop()
    }
}
