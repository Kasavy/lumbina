package dev.lumbina.tv.core.domain.speech

import dev.lumbina.tv.core.testing.FakeSettings
import dev.lumbina.tv.core.testing.FakeSpeechModels
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VoiceLanguagesTest {

    private val catalog = listOf(SpeechModelInfo("de", 3), SpeechModelInfo("en", 2), SpeechModelInfo("ru", 1))
    private val repository = FakeSettings(VoiceSettings(listOf("ru", "en")))
    private val models = FakeSpeechModels(catalog, installed = setOf("ru"), instant = false)
    private val languages = VoiceLanguages(models, repository) { listOf("ru", "en") }

    @Test
    fun `lists enabled languages first in order of preference`() = runTest {
        val state = languages.state.first()
        assertEquals(listOf("ru", "en", "de"), state.languages.map { it.language })
        assertEquals(listOf(true, true, false), state.languages.map { it.enabled })
        assertEquals(2L, state.missingBytes)
        assertEquals(listOf(VoiceLanguageMode.Auto, VoiceLanguageMode.Only("ru")), state.modes)
    }

    @Test
    fun `turning a language on appends it and downloads it`() = runTest {
        languages.setEnabled("de", enabled = true)
        assertEquals(listOf("ru", "en", "de"), repository.current.value.languages)
        assertEquals(listOf("de"), models.installs)
    }

    @Test
    fun `turning off the language the mode is fixed on switches to auto`() = runTest {
        repository.update { it.copy(mode = VoiceLanguageMode.Only("ru")) }
        languages.setEnabled("ru", enabled = false)
        assertEquals(VoiceSettings(listOf("en"), VoiceLanguageMode.Auto), repository.current.value)
    }

    @Test
    fun `removing a language forgets it and deletes the model`() = runTest {
        languages.remove("ru")
        assertEquals(listOf("en"), repository.current.value.languages)
        assertEquals(listOf("ru"), models.deletes)
    }

    @Test
    fun `installing downloads what was offered`() = runTest {
        languages.install(listOf("en"))
        assertEquals(listOf("en"), models.installs)
    }

    @Test
    fun `voice follows Lumbina's language when it has a model, and not otherwise`() = runTest {
        languages.followAppLanguage("de-DE", previous = null)
        assertEquals(VoiceSettings(listOf("de"), VoiceLanguageMode.Auto), repository.current.value)
        languages.followAppLanguage("fr", previous = "de-DE")
        languages.followAppLanguage(null, previous = "de-DE")
        assertEquals(listOf("de"), repository.current.value.languages)
    }

    @Test
    fun `a second choice of Lumbina's language is followed too`() = runTest {
        languages.followAppLanguage("de", previous = null)
        languages.followAppLanguage("en", previous = "de")
        assertEquals(listOf("en"), repository.current.value.languages)
    }

    @Test
    fun `voice languages the user set are kept`() = runTest {
        repository.update { it.copy(languages = listOf("ru", "de")) }
        languages.followAppLanguage("en", previous = null)
        assertEquals(listOf("ru", "de"), repository.current.value.languages)
    }
}
