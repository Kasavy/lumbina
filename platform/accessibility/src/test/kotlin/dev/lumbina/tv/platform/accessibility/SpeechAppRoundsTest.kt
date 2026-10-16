package dev.lumbina.tv.platform.accessibility

import android.os.Bundle
import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpeechAppRoundsTest {
    private var now = 0L
    private var held = true
    private var asked = 0
    private val answers = Answers(startInParts = false, isHeld = { held }, now = { now }) { asked++ }

    private fun readings(vararg texts: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(*texts))
    }

    private fun round(result: String, lasted: Long = 2_000) {
        now += lasted
        answers.onResults(readings(result))
    }

    @Test
    fun `rounds that end while the key is held are joined, and another is asked for`() {
        round("open")
        answers.onPartialResults(readings("smart"))
        assertEquals("open smart", answers.text)
        round("smart tube")

        assertEquals("open smart tube", answers.text)
        assertEquals(2, asked)
        assertFalse(answers.isEnded)
    }

    @Test
    fun `a round with an empty result keeps what it heard last`() {
        answers.onPartialResults(readings("home alone"))
        round("")
        assertEquals("home alone", answers.text)
    }

    @Test
    fun `once the key is up the round that ends is the last`() {
        held = false
        round("open")

        assertEquals(0, asked)
        assertTrue(answers.isEnded)
        assertNull(answers.error)
    }

    @Test
    fun `hearing nothing is no failure, and ends the listening with what was heard`() {
        round("open")
        now += 2_000
        answers.onError(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(1, asked)
        assertTrue(answers.isEnded)
        assertNull(answers.error)
        assertEquals("open", answers.text)
    }

    @Test
    fun `a silence with the key held asks for no other round`() {
        now += 9_000
        answers.onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        assertEquals(0, asked)
        assertTrue(answers.isEnded)
        assertNull(answers.error)
        assertEquals("", answers.text)
    }

    @Test
    fun `a failure ends the listening with what was heard`() {
        round("open")
        now += 2_000
        answers.onError(SpeechRecognizer.ERROR_NETWORK)
        assertEquals(1, asked)
        assertTrue(answers.isEnded)
        assertEquals(SpeechRecognizer.ERROR_NETWORK, answers.error)
        assertEquals("open", answers.text)
    }

    @Test
    fun `an app whose rounds end as they start is not asked again`() {
        round("", lasted = Answers.MIN_ROUND_MILLIS - 1)

        assertEquals(0, asked)
        assertTrue(answers.isEnded)
    }

    @Test
    fun `the rounds have an end though the key stays down`() {
        repeat(Answers.MAX_ROUNDS + 5) { round("a") }

        assertEquals(Answers.MAX_ROUNDS - 1, asked)
        assertTrue(answers.isEnded)
    }

    @Test
    fun `the one who waits for the app to listen is let go by a failure, with the failure there to read`() {
        held = false
        answers.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)

        assertEquals(0L, answers.ready.count)
        assertEquals(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, answers.error)
    }
}
