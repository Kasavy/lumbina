package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.speech.ModelState
import dev.lumbina.tv.core.domain.speech.SpeechApp
import dev.lumbina.tv.core.domain.speech.SpeechApps
import dev.lumbina.tv.core.domain.speech.SpeechKeys
import dev.lumbina.tv.core.domain.speech.SpeechProvider
import dev.lumbina.tv.core.domain.speech.VoiceLanguage
import dev.lumbina.tv.core.domain.speech.VoiceLanguageMode
import dev.lumbina.tv.core.domain.speech.VoiceLanguages
import dev.lumbina.tv.core.domain.speech.VoiceLanguagesState
import dev.lumbina.tv.core.domain.speech.VoiceSettingsRepository
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val voiceLanguages: VoiceLanguages,
    private val voiceSettings: VoiceSettingsRepository,
    speechKeys: SpeechKeys,
    private val speechApps: SpeechApps,
    installedApps: InstalledApps,
    profile: DeviceProfile,
) : ViewModel() {

    /** Voice input only works on devices whose remote mic Lumbina can read. */
    val voiceSupported: Boolean = profile.hasRemoteMic

    val state: StateFlow<VoiceLanguagesState?> =
        voiceLanguages.state.stateIn(viewModelScope, WhileUiSubscribed, null)

    /** Whether phrases like "open Kinopoisk" are carried out rather than typed. */
    val commands: StateFlow<Boolean> = voiceSettings.settings.map { it.commands }
        .stateIn(viewModelScope, WhileUiSubscribed, true)

    /** Who recognizes, and which cloud services have a key. */
    val recognizer: StateFlow<Recognizer> =
        // Asked again when apps change: the one that recognizes may be installed or removed meanwhile.
        combine(voiceSettings.settings, speechKeys.providers, installedApps.observe()) { settings, withKey, _ ->
            Recognizer(settings.provider, withKey, speechApps.installed().firstOrNull())
        }
            .stateIn(viewModelScope, WhileUiSubscribed, Recognizer())

    fun chooseProvider(provider: SpeechProvider) {
        viewModelScope.launch { voiceSettings.update { it.copy(provider = provider) } }
    }

    fun toggleCommands() {
        viewModelScope.launch { voiceSettings.update { it.copy(commands = !it.commands) } }
    }

    /** Turns the language on or off; on an enabled language whose download failed, retries it. */
    fun onLanguageClick(language: VoiceLanguage) {
        val retry = language.enabled && (language.model == ModelState.Missing || language.model is ModelState.Failed)
        viewModelScope.launch { voiceLanguages.setEnabled(language.language, enabled = retry || !language.enabled) }
    }

    fun remove(language: VoiceLanguage) {
        viewModelScope.launch { voiceLanguages.remove(language.language) }
    }

    fun setMode(mode: VoiceLanguageMode) {
        viewModelScope.launch { voiceLanguages.setMode(mode) }
    }
}

/** The recognition [provider] and the cloud services with a key ([withKey]). */
data class Recognizer(
    val provider: SpeechProvider = SpeechProvider.Device,
    val withKey: Set<SpeechProvider> = emptySet(),
    /** Another app that can recognize here; null where there is none to offer. */
    val app: SpeechApp? = null,
) {
    /** A cloud service is chosen, but its key isn't set. */
    val missingKey: Boolean get() = provider.isCloud && provider !in withKey

    /** Another app recognizes: Lumbina's own languages and their mode have no say. */
    val byApp: Boolean get() = provider == SpeechProvider.App && app != null
}
