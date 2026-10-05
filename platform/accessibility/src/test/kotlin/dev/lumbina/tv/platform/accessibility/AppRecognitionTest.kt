package dev.lumbina.tv.platform.accessibility

import dev.lumbina.tv.core.domain.speech.Hypothesis
import dev.lumbina.tv.core.domain.speech.VoiceEvent
import dev.lumbina.tv.core.domain.speech.VoiceStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppRecognitionTest {

    private fun final(best: Hypothesis?, vararg all: Hypothesis) = VoiceEvent.Final(
        best,
        VoiceStats(audioMillis = 1_000, finishedAfterReleaseMillis = 0, hypotheses = all.toList()),
    )

    private val ru = Hypothesis("ru", "один дома", 0.9)
    private val en = Hypothesis("en", "one doma", 0.6)

    @Test
    fun `the best reading comes first, then the other languages', each once`() {
        val readings = final(ru, ru, en, Hypothesis("de", "", 0.0)).readings(max = 5)
        assertEquals(listOf("один дома", "one doma"), readings.map { it.text })
    }

    @Test
    fun `no more readings than the app asked for, and at least one`() {
        assertEquals(listOf("один дома"), final(ru, ru, en).readings(max = 1).map { it.text })
        assertEquals(listOf("один дома"), final(ru, ru, en).readings(max = 0).map { it.text })
    }

    @Test
    fun `nothing heard gives no readings`() {
        assertEquals(emptyList<Hypothesis>(), final(null, Hypothesis("ru", "", 0.0)).readings(max = 5))
    }

    @Test
    fun `readings are what the app hears`() {
        assertEquals(AppSpeech.Heard(listOf(ru)), final(ru, ru, en).forApp(max = 1))
    }

    @Test
    fun `audio that wasn't recognized, being silence, is no speech`() {
        assertEquals(AppSpeech.NoSpeech, final(null).forApp(max = 5))
    }

    @Test
    fun `speech that gave no words is not understood`() {
        assertEquals(AppSpeech.NotUnderstood, final(null, Hypothesis("ru", "", 0.0)).forApp(max = 5))
    }

    @Test
    fun `an app's language is used when recognition has it`() {
        assertEquals(listOf("ru"), requestedLanguages("ru-RU", listOf("en", "ru")))
        assertEquals(listOf("en"), requestedLanguages("en", listOf("en", "ru")))
    }

    @Test
    fun `otherwise voice input's own choice applies`() {
        assertNull(requestedLanguages(null, listOf("en", "ru")))
        assertNull(requestedLanguages("de-DE", listOf("en", "ru")))
        assertNull(requestedLanguages("", listOf("en", "ru")))
    }
}
