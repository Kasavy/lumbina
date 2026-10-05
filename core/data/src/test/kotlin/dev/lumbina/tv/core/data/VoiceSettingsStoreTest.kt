package dev.lumbina.tv.core.data

import androidx.datastore.core.DataStoreFactory
import dev.lumbina.tv.core.domain.speech.VoiceLanguageMode
import dev.lumbina.tv.core.domain.speech.VoiceSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoiceSettingsStoreTest {

    @get:Rule val temp = TemporaryFolder()

    private fun repository(file: File, scope: CoroutineScope) = DataStoreSettings(
        DataStoreFactory.create(
            JsonSerializer(StoredVoiceSettings.serializer(), StoredVoiceSettings()),
            scope = scope,
            produceFile = { file },
        ),
        toDomain = { it.toDomain { listOf("ru", "en") } },
        toStored = VoiceSettings::toStored,
    )

    @Test
    fun `defaults until the user chooses, then round-trips`() = runTest {
        val file = File(temp.root, "voice.json")
        val repository = repository(file, backgroundScope)
        assertEquals(VoiceSettings(listOf("ru", "en"), VoiceLanguageMode.Auto), repository.settings.first())

        repository.update { VoiceSettings(listOf("en", "de"), VoiceLanguageMode.Only("de")) }
        assertEquals(VoiceSettings(listOf("en", "de"), VoiceLanguageMode.Only("de")), repository.settings.first())
        val stored = """{"languages":["en","de"],"onlyLanguage":"de","commands":true,"provider":null}"""
        assertEquals(stored, file.readText())
    }

    @Test
    fun `an empty choice stays empty instead of reverting to defaults`() = runTest {
        val repository = repository(File(temp.root, "voice.json"), backgroundScope)
        repository.update { it.copy(languages = emptyList()) }
        assertEquals(emptyList<String>(), repository.settings.first().languages)
    }
}
