package dev.lumbina.tv.core.domain.speech

import dev.lumbina.tv.core.domain.settings.SettingsRepository

/**
 * What voice input listens for. [languages] are the ones the user turned on, preferred first
 * (the first wins ties when the language is picked automatically); each loaded model costs
 * memory and every utterance runs through all of them. With [commands] on, phrases such as
 * "open settings" or "louder" are carried out instead of typed. [provider] recognizes: the device,
 * or a cloud service with the user's key (the languages then only tell it what to expect).
 */
data class VoiceSettings(
    val languages: List<String>,
    val mode: VoiceLanguageMode = VoiceLanguageMode.Auto,
    val commands: Boolean = true,
    val provider: SpeechProvider = SpeechProvider.Device,
)

/** Persisted [VoiceSettings], shared by the launcher UI and the process that runs voice input. */
typealias VoiceSettingsRepository = SettingsRepository<VoiceSettings>

/** Languages to start with before the user has chosen any (e.g. the device language). */
fun interface DefaultVoiceLanguages {
    fun get(): List<String>
}
