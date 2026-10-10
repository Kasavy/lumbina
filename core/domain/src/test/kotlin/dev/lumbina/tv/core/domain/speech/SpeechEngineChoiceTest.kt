package dev.lumbina.tv.core.domain.speech

import dev.lumbina.tv.core.testing.FakeSpeechEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeechEngineChoiceTest {

    private val device = FakeSpeechEngine(listOf(Hypothesis("ru", "с устройства", 1.0)))
    private val openAi = FakeCloud()
    private val choice = SpeechEngineChoice(device, mapOf(SpeechProvider.OpenAi to openAi))

    @Test
    fun `the device recognizes until a cloud provider is chosen`() {
        assertTrue(choice.prepare(listOf("ru")))
        assertEquals(SpeechProvider.Device, choice.provider)
        assertEquals(device.languages, choice.languages)
    }

    @Test
    fun `a chosen cloud provider gets its key, and recognizes only with one`() {
        choice.choose(SpeechSetup(SpeechProvider.OpenAi, key = null, languages = listOf("ru")))
        assertEquals(SpeechProvider.OpenAi, choice.provider)
        assertFalse(choice.prepare(listOf("ru")))

        choice.choose(SpeechSetup(SpeechProvider.OpenAi, key = "sk-test", languages = listOf("ru")))
        assertEquals("sk-test", openAi.key)
        assertTrue(choice.prepare(listOf("ru")))
    }

    @Test
    fun `another app recognizes when it is chosen, and where there is none the device does`() {
        val app = FakeSpeechEngine(listOf(Hypothesis("ru", "from the app", 1.0)))
        val withApp = SpeechEngineChoice(device, emptyMap(), app)
        withApp.choose(SpeechSetup(SpeechProvider.App, key = null, languages = listOf("ru")))
        assertEquals(SpeechProvider.App, withApp.provider)

        choice.choose(SpeechSetup(SpeechProvider.App, key = null, languages = listOf("ru")))
        assertEquals(SpeechProvider.Device, choice.provider)
    }

    @Test
    fun `another app that is gone leaves the device to recognize`() {
        val gone = FakeSpeechEngine(emptyList())
        val withApp = SpeechEngineChoice(device, emptyMap(), gone)
        withApp.choose(SpeechSetup(SpeechProvider.App, key = null, languages = listOf("ru")))
        assertTrue(withApp.prepare(listOf("ru")))
        assertEquals(SpeechProvider.Device, withApp.provider)
    }

    @Test
    fun `a provider without an engine falls back to the device`() {
        choice.choose(SpeechSetup(SpeechProvider.Yandex, key = "key", languages = listOf("ru")))
        assertEquals(SpeechProvider.Device, choice.provider)
    }

    private class FakeCloud :
        CloudSpeechEngine,
        SpeechEngine by FakeSpeechEngine(emptyList()) {
        var key: String? = null

        override fun useKey(key: String?) {
            this.key = key
        }

        override fun useVocabulary(words: List<String>) = Unit

        override fun prepare(languages: List<String>): Boolean = key != null
    }
}
