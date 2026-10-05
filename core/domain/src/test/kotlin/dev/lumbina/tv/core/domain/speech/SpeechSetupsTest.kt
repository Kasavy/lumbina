package dev.lumbina.tv.core.domain.speech

import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.home.HomeLayout
import dev.lumbina.tv.core.domain.home.HomeLayoutRepository
import dev.lumbina.tv.core.testing.FakeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpeechSetupsTest {

    private val vlc = App(AppId("org.videolan.vlc", "Main"), "VLC", isTvApp = true)
    private val settings = FakeSettings(VoiceSettings(listOf("ru", "en")))
    private val storedKeys = MutableStateFlow(mapOf(SpeechProvider.OpenAi to "sk-test"))
    private val layout = MutableStateFlow(HomeLayout())

    private val keys = object : SpeechKeys {
        override val keys: Flow<Map<SpeechProvider, String>> = storedKeys
        override val providers: Flow<Set<SpeechProvider>> = storedKeys.map { it.keys }

        override suspend fun set(provider: SpeechProvider, key: String) = true
    }

    private val layouts = object : HomeLayoutRepository {
        override val layout: Flow<HomeLayout> = this@SpeechSetupsTest.layout

        override suspend fun update(transform: (HomeLayout) -> HomeLayout) =
            this@SpeechSetupsTest.layout.update(transform)
    }

    private val setups = SpeechSetups(settings, keys, { MutableStateFlow(listOf(vlc)) }, layouts)

    @Test
    fun `the device's recognizer gets no key and no app names`() = runTest {
        assertEquals(SpeechSetup(SpeechProvider.Device, null, listOf("ru", "en")), setups.setups().first())
    }

    @Test
    fun `a cloud provider gets its key and the apps' names, custom ones as of the latest refresh`() = runTest {
        settings.current.update { it.copy(provider = SpeechProvider.OpenAi) }
        val refresh = MutableSharedFlow<Unit>()
        val seen = mutableListOf<SpeechSetup>()
        val collecting = launch(UnconfinedTestDispatcher(testScheduler)) { setups.setups(refresh).toList(seen) }

        assertEquals(SpeechSetup(SpeechProvider.OpenAi, "sk-test", listOf("ru", "en"), listOf("VLC")), seen.last())
        layout.value = HomeLayout().rename(vlc.id, "Player")
        assertEquals(listOf("VLC"), seen.last().vocabulary)
        refresh.emit(Unit)
        assertEquals(listOf("Player", "VLC"), seen.last().vocabulary)

        settings.current.update { it.copy(provider = SpeechProvider.Yandex) }
        assertEquals(SpeechSetup(SpeechProvider.Yandex, null, listOf("ru", "en"), listOf("Player", "VLC")), seen.last())
        collecting.cancel()
    }
}
