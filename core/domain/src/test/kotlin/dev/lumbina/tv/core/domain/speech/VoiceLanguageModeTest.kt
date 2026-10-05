package dev.lumbina.tv.core.domain.speech

import dev.lumbina.tv.core.testing.FakeSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceLanguageModeTest {

    private val installed = listOf("ru", "en")
    private val repository = FakeSettings(VoiceSettings(listOf("ru", "en")))

    @Test
    fun `cycles auto, each language, auto and saves the mode`() = runTest {
        val setting = VoiceLanguageSetting(available = { installed }, repository, backgroundScope)
        runCurrent()
        assertEquals(listOf("ru", "en"), setting.languages())
        assertEquals(VoiceLanguageMode.Only("ru"), setting.cycle())
        assertEquals(listOf("ru"), setting.languages())
        runCurrent()
        assertEquals(VoiceLanguageMode.Only("ru"), repository.current.value.mode)
        assertEquals(VoiceLanguageMode.Only("en"), setting.cycle())
        assertEquals(listOf("en"), setting.languages())
        assertEquals(VoiceLanguageMode.Auto, setting.cycle())
    }

    @Test
    fun `starts with the saved mode and follows changes made elsewhere`() = runTest {
        repository.current.value = repository.current.value.copy(mode = VoiceLanguageMode.Only("en"))
        val setting = VoiceLanguageSetting(available = { installed }, repository, backgroundScope)
        runCurrent()
        assertEquals(listOf("en"), setting.languages())
        repository.update { it.copy(mode = VoiceLanguageMode.Auto) }
        runCurrent()
        assertEquals(VoiceLanguageMode.Auto, setting.mode)
    }

    @Test
    fun `a stored value read before the save lands does not undo a switch`() = runTest {
        val slow = object : VoiceSettingsRepository by repository {
            override suspend fun update(transform: (VoiceSettings) -> VoiceSettings) {
                delay(100)
                repository.update(transform)
            }
        }
        val setting = VoiceLanguageSetting(available = { installed }, slow, backgroundScope)
        assertEquals(VoiceLanguageMode.Only("ru"), setting.cycle())
        runCurrent()
        assertEquals(VoiceLanguageMode.Only("ru"), setting.mode)
        advanceTimeBy(200) // background work isn't run by advanceUntilIdle
        runCurrent()
        assertEquals(VoiceLanguageMode.Only("ru"), repository.current.value.mode)
        assertEquals(VoiceLanguageMode.Only("ru"), setting.mode)
    }

    @Test
    fun `with one language there is nothing to switch`() {
        assertEquals(VoiceLanguageMode.Auto, VoiceLanguageMode.Auto.next(listOf("ru")))
    }

    @Test
    fun `a missing language falls back to all installed`() {
        assertEquals(listOf("ru"), VoiceLanguageMode.Only("de").languages(listOf("ru")))
    }
}
